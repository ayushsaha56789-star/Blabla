import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, DestroyRef, inject, input, OnInit, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { problemOf } from '../../core/errors';
import { FeaturesService } from '../../core/features.service';
import { currentFix, SafetyApi, SosResult, TripShare } from '../../core/safety-api';
import { ToastService } from '../../core/toast.service';
import { Icon } from '../../shared/icon';

/**
 * Safety tools on a ride page, for the driver and passengers with a confirmed seat: share the trip with family (with
 * live location if wanted) and, around the trip, an SOS button.
 */
@Component({
  selector: 'app-trip-safety',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [DatePipe, RouterLink, Icon],
  host: { class: 'block' },
  template: `
    <section class="card p-5 sm:p-6" aria-labelledby="safety-heading">
      <h2 id="safety-heading" class="kicker mb-4">Safety</h2>

      @if (features.isOn('safety.sos') && sosWindow()) {
        @switch (sosState()) {
          @case ('idle') {
            <button type="button" class="btn w-full bg-rose-600 py-4 text-base text-white shadow-lg shadow-rose-600/25 hover:bg-rose-700" (click)="sosState.set('confirm')">
              <app-icon name="shieldAlert" [size]="22" /> SOS — I need help
            </button>
          }
          @case ('confirm') {
            <div class="rounded-2xl border-2 border-rose-500 p-4" role="alertdialog" aria-labelledby="sos-title">
              <p id="sos-title" class="font-bold text-rose-700 dark:text-rose-300">Get help now</p>
              <p class="mt-1 text-sm text-slate-600 dark:text-slate-400">In danger? Call the police first. Then let SaathChalo and your emergency contacts know.</p>
              <div class="mt-3 grid gap-2 sm:grid-cols-2">
                <a href="tel:112" class="btn bg-rose-600 py-3.5 text-white hover:bg-rose-700"><app-icon name="phone" [size]="18" /> Call 112</a>
                <button type="button" class="btn btn-secondary py-3.5" (click)="raiseSos()" [disabled]="sosBusy()">
                  {{ sosBusy() ? 'Sending alert…' : 'Alert SaathChalo & my contacts' }}
                </button>
              </div>
              <button type="button" class="btn btn-ghost btn-sm mt-2" (click)="sosState.set('idle')">I'm okay, go back</button>
            </div>
          }
          @case ('sent') {
            <div class="rounded-2xl bg-rose-50 p-4 text-sm text-rose-900 dark:bg-rose-500/10 dark:text-rose-100" role="status">
              <p class="font-bold">Alert sent at {{ sosResult()?.createdAt | date: 'h:mm a' }}</p>
              <p class="mt-1">SaathChalo support can see your alert{{ sosResult()?.sharePath ? ' and follow your trip' : '' }}.
                @if (sosResult()?.contactsNotified) { We texted {{ sosResult()!.contactsNotified }} emergency {{ sosResult()!.contactsNotified === 1 ? 'contact' : 'contacts' }}. }
                @else { You have no emergency contacts yet, so nobody was texted. <a routerLink="/profile" fragment="safety" class="font-semibold underline">Add them</a>. }
              </p>
              <a href="tel:112" class="btn mt-3 w-full bg-rose-600 py-3 text-white hover:bg-rose-700"><app-icon name="phone" [size]="18" /> Call 112</a>
            </div>
          }
        }
      }

      @if (features.isOn('safety.trip_share')) {
        <div [class.mt-5]="features.isOn('safety.sos') && sosWindow()">
          @if (share(); as s) {
            <p class="text-sm font-semibold">Your trip link is on</p>
            <p class="text-xs text-slate-500 dark:text-slate-400">Anyone with the link can follow this trip until {{ s.expiresAt | date: 'd MMM, h:mm a' }}. No phone numbers are shown.</p>
            <div class="mt-3 flex flex-wrap gap-2">
              <button type="button" class="btn btn-secondary btn-sm" (click)="sendLink()"><app-icon name="external" [size]="16" /> Send the link</button>
              <button type="button" class="btn btn-ghost btn-sm" (click)="copy()"><app-icon name="copy" [size]="16" /> Copy</button>
              <button type="button" class="btn btn-ghost btn-sm text-rose-600 dark:text-rose-400" (click)="stop()" [disabled]="busy()">Stop sharing</button>
            </div>
            <div class="mt-4 flex items-center justify-between gap-4 rounded-2xl bg-slate-50 p-3 dark:bg-slate-800/60">
              <span id="live-label" class="text-sm">
                <span class="block font-semibold">Share my live location</span>
                <span class="block text-xs text-slate-500 dark:text-slate-400">
                  @if (live()) { {{ lastSent() ? 'Last sent ' + (lastSent()! | date: 'h:mm a') : 'Finding your location…' }}. Keep this page open. }
                  @else { Only your latest position is kept, and only until shortly after the trip. }
                </span>
              </span>
              <button type="button" role="switch" class="switch" [attr.aria-checked]="live()" aria-labelledby="live-label" (click)="toggleLive()"><span class="switch-knob"></span></button>
            </div>
          } @else {
            <p class="text-sm text-slate-600 dark:text-slate-400">Let family or friends follow this trip with a private link.</p>
            <button type="button" class="btn btn-secondary mt-3 w-full sm:w-auto" (click)="startSharing()" [disabled]="busy()"><app-icon name="route" [size]="18" /> Share my trip</button>
          }
        </div>
      }
    </section>
  `,
})
export class TripSafety implements OnInit {
  private readonly api = inject(SafetyApi);
  private readonly toast = inject(ToastService);
  protected readonly features = inject(FeaturesService);

  readonly rideId = input.required<string>();
  readonly departureAt = input.required<string>();
  readonly arrivalAt = input.required<string>();
  readonly routeLabel = input('');

  protected readonly share = signal<TripShare | null>(null);
  protected readonly busy = signal(false);
  protected readonly live = signal(false);
  protected readonly lastSent = signal<Date | null>(null);
  protected readonly sosState = signal<'idle' | 'confirm' | 'sent'>('idle');
  protected readonly sosBusy = signal(false);
  protected readonly sosResult = signal<SosResult | null>(null);

  /** SOS shows from 2 hours before departure until 3 hours after the planned arrival. */
  protected readonly sosWindow = computed(() => {
    const now = Date.now();
    return now >= Date.parse(this.departureAt()) - 2 * 3600_000 && now <= Date.parse(this.arrivalAt()) + 3 * 3600_000;
  });

  private watchId: number | null = null;
  private lastSentAt = 0;
  private lastFix: { lat: number; lng: number; accuracy: number; at: number } | null = null;

  constructor() {
    inject(DestroyRef).onDestroy(() => this.stopWatching());
  }

  async ngOnInit(): Promise<void> {
    if (!this.features.isOn('safety.trip_share')) return;
    try {
      this.share.set(await this.api.currentShare(this.rideId()));
    } catch {
      // Not critical.
    }
  }

  protected async startSharing(): Promise<void> {
    this.busy.set(true);
    try {
      this.share.set(await this.api.share(this.rideId()));
      await this.sendLink();
    } catch (e) {
      this.toast.error(problemOf(e).detail);
    } finally {
      this.busy.set(false);
    }
  }

  protected async sendLink(): Promise<void> {
    const s = this.share();
    if (!s) return;
    const url = location.origin + s.path;
    try {
      if (navigator.share) await navigator.share({ title: 'Follow my trip on SaathChalo', text: `I'm travelling ${this.routeLabel()}. Follow my trip:`, url });
      else await this.copy();
    } catch {
      // Share sheet closed.
    }
  }

  protected async copy(): Promise<void> {
    const s = this.share();
    if (!s) return;
    try {
      await navigator.clipboard.writeText(location.origin + s.path);
      this.toast.success('Link copied. Send it to someone you trust.');
    } catch {
      this.toast.info(location.origin + s.path);
    }
  }

  protected async stop(): Promise<void> {
    this.busy.set(true);
    try {
      await this.api.stopSharing(this.rideId());
      this.stopWatching();
      this.share.set(null);
      this.toast.success('Trip link turned off.');
    } catch (e) {
      this.toast.error(problemOf(e).detail);
    } finally {
      this.busy.set(false);
    }
  }

  protected toggleLive(): void {
    if (this.live()) {
      this.stopWatching();
      return;
    }
    if (!('geolocation' in navigator)) {
      this.toast.error("Your browser can't share your location.");
      return;
    }
    this.live.set(true);
    this.lastSentAt = 0;
    this.watchId = navigator.geolocation.watchPosition(
      (p) => {
        this.lastFix = { lat: p.coords.latitude, lng: p.coords.longitude, accuracy: Math.round(p.coords.accuracy), at: Date.now() };
        if (Date.now() - this.lastSentAt < 25_000) return;
        this.lastSentAt = Date.now();
        this.api
          .sendLocation(this.rideId(), { lat: p.coords.latitude, lng: p.coords.longitude, accuracy: Math.round(p.coords.accuracy) })
          .then(() => this.lastSent.set(new Date()))
          .catch((e) => {
            this.toast.error(problemOf(e).detail);
            this.stopWatching();
          });
      },
      () => {
        this.toast.error("We couldn't get your location. Check that location is allowed for this site.");
        this.stopWatching();
      },
      { enableHighAccuracy: true, maximumAge: 15_000 },
    );
  }

  protected async raiseSos(): Promise<void> {
    this.sosBusy.set(true);
    try {
      // Use a fresh position if the phone gives one quickly, else the last one from live sharing (if recent).
      const recent = this.lastFix && Date.now() - this.lastFix.at < 120_000 ? this.lastFix : null;
      const fix = (await currentFix(4000)) ?? (recent ? { lat: recent.lat, lng: recent.lng, accuracy: recent.accuracy } : null);
      this.sosResult.set(await this.api.sos(this.rideId(), fix));
      this.sosState.set('sent');
    } catch (e) {
      this.toast.error(problemOf(e).detail + ' Please call 112.');
    } finally {
      this.sosBusy.set(false);
    }
  }

  private stopWatching(): void {
    if (this.watchId !== null) navigator.geolocation.clearWatch(this.watchId);
    this.watchId = null;
    this.live.set(false);
  }
}
