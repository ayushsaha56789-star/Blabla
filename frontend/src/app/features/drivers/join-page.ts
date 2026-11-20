import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, inject, OnInit, signal } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { DriversApi, LICENCE_LABELS, LicenceClass, MyDriverProfile } from '../../core/drivers-api';
import { problemOf } from '../../core/errors';
import { FeaturesService } from '../../core/features.service';
import { fromLocalInput } from '../../core/rentals-api';
import { rupees } from '../../core/rides-format';
import { Place } from '../../core/rides.models';
import { ToastService } from '../../core/toast.service';
import { Icon } from '../../shared/icon';
import { PlaceInput } from '../../shared/place-input';

/** Becoming a driver partner, and running it afterwards: details, areas, the checks, time off. */
@Component({
  selector: 'app-join-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [DatePipe, RouterLink, Icon, PlaceInput],
  template: `
    <div class="container-page max-w-3xl py-6 sm:py-10">
      <a routerLink="/drivers" class="btn btn-ghost btn-sm -ml-2"><app-icon name="arrowLeft" [size]="16" /> Hire a driver</a>
      <p class="kicker mt-3">Drive with SaathChalo</p>
      <h1 class="mt-1 text-3xl font-extrabold tracking-tight">{{ profile() ? 'Your driver profile' : 'Become a driver partner' }}</h1>
      @if (!profile()) {
        <p class="mt-2 text-slate-600 dark:text-slate-400">Drive members' own cars in the areas you choose, at your own prices. You need a valid driving licence; our team checks it before members can book you.</p>
      }

      @if (loading()) {
        <div class="skeleton mt-6 h-40"></div>
      } @else {
        @if (profile(); as p) {
          <section class="card mt-6 p-5 sm:p-6" aria-labelledby="status-heading">
            <div class="flex flex-wrap items-center justify-between gap-2">
              <h2 id="status-heading" class="text-lg font-bold">Status</h2>
              <span class="badge" [class]="statusTone()">{{ statusText() }}</span>
            </div>
            @if (p.reviewNote) { <p class="mt-3 rounded-2xl bg-accent-100/60 p-3 text-sm dark:bg-accent-600/10"><b>From our team:</b> {{ p.reviewNote }}</p> }
            @if (p.status === 'LIVE') {
              <div class="mt-4 flex flex-wrap items-center justify-between gap-3">
                <div>
                  <p class="font-semibold">Taking bookings</p>
                  <p class="text-sm text-slate-600 dark:text-slate-400">{{ p.takingBookings ? 'Members can find and book you.' : 'You’re hidden from search.' }}</p>
                </div>
                <button type="button" role="switch" class="switch" [attr.aria-checked]="p.takingBookings" aria-label="Taking bookings" (click)="toggleTaking()" [disabled]="busy()"><span class="switch-knob"></span></button>
              </div>
              <div class="mt-4 grid grid-cols-3 gap-2 text-center">
                <div class="rounded-2xl bg-slate-50 p-3 dark:bg-slate-800/60"><p class="text-xl font-extrabold">{{ p.tripsDone }}</p><p class="text-xs text-slate-500 dark:text-slate-400">trips</p></div>
                <div class="rounded-2xl bg-slate-50 p-3 dark:bg-slate-800/60"><p class="text-xl font-extrabold">{{ p.rating ?? '–' }}</p><p class="text-xs text-slate-500 dark:text-slate-400">rating</p></div>
                <a routerLink="/drivers/trips" class="rounded-2xl bg-brand-50 p-3 dark:bg-brand-500/15"><p class="text-xl font-extrabold text-brand-700 dark:text-brand-300">{{ p.requestsWaiting }}</p><p class="text-xs text-brand-700 dark:text-brand-300">waiting</p></a>
              </div>
            } @else {
              <ul class="mt-4 space-y-2 text-sm">
                <li class="flex items-start gap-2"><app-icon [name]="p.hasPhoto ? 'checkCircle' : 'info'" [size]="18" [class]="p.hasPhoto ? 'text-brand-600' : 'text-slate-400'" />
                  <span>A clear profile photo @if (!p.hasPhoto) { · <a routerLink="/profile" class="font-semibold text-brand-700 underline dark:text-brand-400">Add it</a> }</span></li>
                <li class="flex items-start gap-2"><app-icon [name]="p.licenceStatus === 'APPROVED' ? 'checkCircle' : 'info'" [size]="18" [class]="p.licenceStatus === 'APPROVED' ? 'text-brand-600' : 'text-slate-400'" />
                  <span>Your driving licence checked:
                    @switch (p.licenceStatus) {
                      @case ('APPROVED') { done }
                      @case ('PENDING') { waiting for our team }
                      @default { <a routerLink="/profile" fragment="verification" class="font-semibold text-brand-700 underline dark:text-brand-400">send a photo of it</a> }
                    }</span></li>
                <li class="flex items-start gap-2"><app-icon [name]="p.areas.length ? 'checkCircle' : 'info'" [size]="18" [class]="p.areas.length ? 'text-brand-600' : 'text-slate-400'" />
                  <span>At least one area where you drive (below)</span></li>
              </ul>
              @if (p.status === 'DRAFT' || p.status === 'REJECTED') {
                <button type="button" class="btn btn-primary mt-4" (click)="submit()" [disabled]="busy()"><app-icon name="check" [size]="18" /> Send for checking</button>
              }
            }
          </section>
        }

        <form class="card mt-5 p-5 sm:p-6" (submit)="save($event)" aria-labelledby="details-heading">
          <h2 id="details-heading" class="text-lg font-bold">Your licence and prices</h2>
          <div class="mt-4 grid gap-3 sm:grid-cols-3">
            <div class="sm:col-span-1"><label class="label text-xs" for="j-number">Licence number</label>
              <input id="j-number" class="input" maxlength="24" autocomplete="off" [value]="licenceNumber()" (input)="licenceNumber.set($any($event.target).value)"
                     [placeholder]="profile()?.licenceNumberMasked ? 'Saved: ' + profile()!.licenceNumberMasked : 'e.g. MH1420110062821'" /></div>
            <div><label class="label text-xs" for="j-class">Licence type</label>
              <select id="j-class" class="input" (change)="licenceClass.set($any($event.target).value)">
                @for (c of classes; track c) { <option [value]="c" [selected]="c === licenceClass()">{{ licenceLabels[c] }}</option> }
              </select></div>
            <div><label class="label text-xs" for="j-valid">Valid until</label>
              <input id="j-valid" type="date" class="input" [value]="validUntil()" (input)="validUntil.set($any($event.target).value)" /></div>
            <div><label class="label text-xs" for="j-years">Years driving</label>
              <input id="j-years" type="number" min="0" max="60" class="input" [value]="experience()" (input)="experience.set(+$any($event.target).value)" /></div>
            <div class="sm:col-span-2"><label class="label text-xs" for="j-langs">Languages you speak</label>
              <input id="j-langs" class="input" maxlength="120" [value]="languages()" (input)="languages.set($any($event.target).value)" placeholder="Hindi, English" /></div>
          </div>
          <p class="label mt-4">I drive</p>
          <div class="flex flex-wrap gap-x-5 gap-y-2 text-sm">
            <label class="flex items-center gap-2"><input type="checkbox" class="h-4 w-4 accent-brand-600" [checked]="manual()" (change)="manual.set($any($event.target).checked)" /> Cars with gears</label>
            <label class="flex items-center gap-2"><input type="checkbox" class="h-4 w-4 accent-brand-600" [checked]="automatic()" (change)="automatic.set($any($event.target).checked)" /> Automatic cars</label>
            <label class="flex items-center gap-2"><input type="checkbox" class="h-4 w-4 accent-brand-600" [checked]="outstation()" (change)="outstation.set($any($event.target).checked)" /> Outstation trips</label>
          </div>
          <div class="mt-4 grid gap-3 sm:grid-cols-3">
            <div><label class="label text-xs" for="j-hour">Per hour (₹)</label>
              <input id="j-hour" type="number" min="50" max="2000" class="input" [value]="hourPrice()" (input)="hourPrice.set(+$any($event.target).value)" /></div>
            <div><label class="label text-xs" for="j-day">Full day, up to 12 h (₹)</label>
              <input id="j-day" type="number" min="300" max="10000" class="input" [value]="dayPrice()" (input)="dayPrice.set(+$any($event.target).value)" /></div>
            <div><label class="label text-xs" for="j-night">Night charge (₹)</label>
              <input id="j-night" type="number" min="0" max="2000" class="input" [value]="nightCharge()" (input)="nightCharge.set(+$any($event.target).value)" /></div>
            @if (outstation()) {
              <div><label class="label text-xs" for="j-out">Outstation, per day (₹)</label>
                <input id="j-out" type="number" min="300" max="15000" class="input" [value]="outstationPrice()" (input)="outstationPrice.set(+$any($event.target).value)" /></div>
              <div><label class="label text-xs" for="j-allow">Food and stay, per day (₹)</label>
                <input id="j-allow" type="number" min="0" max="3000" class="input" [value]="allowance()" (input)="allowance.set(+$any($event.target).value)" /></div>
            }
          </div>
          <p class="hint">Members see the full price before booking: hours × your hourly price, never more than your day price, plus the night charge for trips between 10 pm and 6 am. Example: 6 hours = {{ money(example()) }}.</p>
          <label class="label mt-4" for="j-about">About you <span class="font-normal text-slate-500">(optional)</span></label>
          <textarea id="j-about" class="input min-h-20" maxlength="500" [value]="about()" (input)="about.set($any($event.target).value)" placeholder="e.g. 8 years with a family in Gurugram. Calm driver, knows the airport routes."></textarea>
          <div class="mt-4 flex justify-end"><button type="submit" class="btn btn-primary" [disabled]="busy()">{{ profile() ? 'Save changes' : 'Save and continue' }}</button></div>
        </form>

        @if (profile(); as p) {
          <section class="card mt-5 p-5 sm:p-6" aria-labelledby="areas-heading">
            <h2 id="areas-heading" class="text-lg font-bold">Where you drive</h2>
            <p class="mt-1 text-sm text-slate-600 dark:text-slate-400">Members are matched with you when their pickup is inside one of these areas. Up to 5.</p>
            @if (p.areas.length) {
              <ul class="mt-3 flex flex-wrap gap-2">
                @for (a of p.areas; track a.id) {
                  <li class="chip gap-1">{{ a.placeName }} · {{ a.radiusKm }} km
                    <button type="button" class="-mr-1 rounded-full p-0.5 hover:bg-slate-200 dark:hover:bg-slate-700" (click)="removeArea(a.id)" [attr.aria-label]="'Remove ' + a.placeName"><app-icon name="close" [size]="14" /></button></li>
                }
              </ul>
            }
            <div class="mt-3 flex flex-wrap items-end gap-2">
              <div class="min-w-48 flex-1"><label class="label text-xs" for="j-area">Town</label>
                <app-place-input inputId="j-area" [(value)]="newArea" placeholder="Town or city" /></div>
              <div><label class="label text-xs" for="j-radius">How far around</label>
                <select id="j-radius" class="input" (change)="radius.set(+$any($event.target).value)">
                  @for (r of radii; track r) { <option [value]="r" [selected]="r === radius()">{{ r }} km</option> }
                </select></div>
              <button type="button" class="btn btn-secondary" (click)="addArea()" [disabled]="busy() || !newArea()">Add</button>
            </div>
          </section>

          <section class="card mt-5 p-5 sm:p-6" aria-labelledby="police-heading">
            <h2 id="police-heading" class="text-lg font-bold">Police verification <span class="text-sm font-normal text-slate-500">(optional)</span></h2>
            <p class="mt-1 text-sm text-slate-600 dark:text-slate-400">A police verification certificate earns a "Police verified" badge once our team checks it. Only checking staff see it; it's stored encrypted.</p>
            <div class="mt-3 flex flex-wrap items-center gap-2">
              @if (p.policeCertificate) {
                <span class="badge" [class]="p.policeChecked ? 'badge-success' : 'badge-warning'">{{ p.policeChecked ? 'Checked' : 'Waiting for a check' }}</span>
                <label class="btn btn-secondary btn-sm cursor-pointer">Replace<input type="file" accept="image/*" class="sr-only" (change)="upload($event)" /></label>
                <button type="button" class="btn btn-ghost btn-sm text-rose-600 dark:text-rose-400" (click)="removeCertificate()" [disabled]="busy()">Remove</button>
              } @else {
                <label class="btn btn-secondary btn-sm cursor-pointer"><app-icon name="camera" [size]="16" /> Upload a photo<input type="file" accept="image/*" class="sr-only" (change)="upload($event)" /></label>
              }
            </div>
          </section>

          <section class="card mt-5 p-5 sm:p-6" aria-labelledby="off-heading">
            <h2 id="off-heading" class="text-lg font-bold">Time off</h2>
            <p class="mt-1 text-sm text-slate-600 dark:text-slate-400">Members can't book you then.</p>
            @if (p.blocks.length) {
              <ul class="mt-2 space-y-2 text-sm">
                @for (b of p.blocks; track b.id) {
                  <li class="flex items-center gap-2"><span class="flex-1">{{ b.from | date: 'd MMM, h:mm a' }} – {{ b.to | date: 'd MMM, h:mm a' }}@if (b.note) { · {{ b.note }} }</span>
                    <button type="button" class="btn btn-ghost btn-icon" (click)="removeBlock(b.id)" aria-label="Remove"><app-icon name="trash" [size]="16" /></button></li>
                }
              </ul>
            }
            <div class="mt-3 grid gap-2 sm:grid-cols-2">
              <label class="min-w-0 text-sm"><span class="label text-xs">From</span><input type="datetime-local" class="input" [value]="offFrom()" (input)="offFrom.set($any($event.target).value)" /></label>
              <label class="min-w-0 text-sm"><span class="label text-xs">Until</span><input type="datetime-local" class="input" [value]="offTo()" (input)="offTo.set($any($event.target).value)" /></label>
            </div>
            <div class="mt-2 flex flex-wrap items-end gap-2">
              <label class="min-w-48 flex-1 text-sm"><span class="label text-xs">Note (optional)</span><input class="input" maxlength="100" [value]="offNote()" (input)="offNote.set($any($event.target).value)" placeholder="Family wedding" /></label>
              <button type="button" class="btn btn-secondary" (click)="addBlock()" [disabled]="busy() || !offFrom() || !offTo()">Add time off</button>
            </div>
          </section>

          <section class="mt-5 text-center">
            @if (confirmLeave()) {
              <div class="card p-4 text-sm"><p>Stop being a driver partner? Members won't find you any more. You can sign up again later.</p>
                <div class="mt-3 flex justify-center gap-2"><button type="button" class="btn btn-ghost btn-sm" (click)="confirmLeave.set(false)">Keep driving</button>
                  <button type="button" class="btn btn-sm bg-rose-600 text-white hover:bg-rose-700" (click)="leave()" [disabled]="busy()">Stop</button></div></div>
            } @else {
              <button type="button" class="btn btn-ghost btn-sm text-rose-600 dark:text-rose-400" (click)="confirmLeave.set(true)">Stop being a driver partner</button>
            }
          </section>
        }
      }
    </div>
  `,
})
export class JoinPage implements OnInit {
  private readonly api = inject(DriversApi);
  private readonly toast = inject(ToastService);
  private readonly router = inject(Router);
  protected readonly features = inject(FeaturesService);

  protected readonly classes: LicenceClass[] = ['LMV', 'TRANSPORT'];
  protected readonly licenceLabels = LICENCE_LABELS;
  protected readonly radii = [5, 10, 15, 25, 40, 60];
  protected readonly money = rupees;

  protected readonly loading = signal(true);
  protected readonly busy = signal(false);
  protected readonly profile = signal<MyDriverProfile | null>(null);
  protected readonly confirmLeave = signal(false);

  protected readonly licenceNumber = signal('');
  protected readonly licenceClass = signal<LicenceClass>('LMV');
  protected readonly validUntil = signal('');
  protected readonly experience = signal(3);
  protected readonly languages = signal('Hindi, English');
  protected readonly manual = signal(true);
  protected readonly automatic = signal(true);
  protected readonly outstation = signal(true);
  protected readonly hourPrice = signal(150);
  protected readonly dayPrice = signal(1200);
  protected readonly outstationPrice = signal(1500);
  protected readonly allowance = signal(300);
  protected readonly nightCharge = signal(200);
  protected readonly about = signal('');

  protected readonly newArea = signal<Place | null>(null);
  protected readonly radius = signal(15);
  protected readonly offFrom = signal('');
  protected readonly offTo = signal('');
  protected readonly offNote = signal('');

  protected readonly example = computed(() => Math.min(6 * this.hourPrice(), this.dayPrice()));
  protected readonly statusText = computed(() => {
    switch (this.profile()?.status) {
      case 'LIVE': return 'Live';
      case 'IN_REVIEW': return 'Being checked';
      case 'REJECTED': return 'Needs changes';
      default: return 'Not sent yet';
    }
  });
  protected readonly statusTone = computed(() => {
    switch (this.profile()?.status) {
      case 'LIVE': return 'badge-success';
      case 'IN_REVIEW': return 'badge-warning';
      case 'REJECTED': return 'badge-danger';
      default: return 'badge-muted';
    }
  });

  async ngOnInit(): Promise<void> {
    try {
      const p = await this.api.me();
      if (p) this.fill(p);
    } catch (e) {
      this.toast.error(problemOf(e).detail);
    } finally {
      this.loading.set(false);
    }
  }

  protected async save(event: Event): Promise<void> {
    event.preventDefault();
    if (!this.validUntil()) {
      this.toast.error('Add the date your licence is valid until.');
      return;
    }
    await this.run(() => this.api.save({
      licenceNumber: this.licenceNumber().trim() || null, licenceClass: this.licenceClass(), licenceValidUntil: this.validUntil(),
      experienceYears: this.experience(), languages: this.languages().split(',').map((l) => l.trim()).filter(Boolean),
      manual: this.manual(), automatic: this.automatic(), outstation: this.outstation(), hourPrice: this.hourPrice(),
      dayPrice: this.dayPrice(), outstationPrice: this.outstationPrice(), allowance: this.allowance(),
      nightCharge: this.nightCharge(), about: this.about().trim() || null,
    }), 'Saved.');
    this.licenceNumber.set('');
  }

  protected submit(): Promise<void> {
    return this.run(() => this.api.submit(), 'Sent for checking. We usually reply within a day.');
  }

  protected toggleTaking(): Promise<void> {
    const on = !this.profile()?.takingBookings;
    return this.run(() => this.api.takingBookings(on), on ? 'Members can book you again.' : 'You’re hidden from search.');
  }

  protected async addArea(): Promise<void> {
    const place = this.newArea();
    if (!place) return;
    await this.run(() => this.api.addArea(place.id, this.radius()), 'Area added.');
    this.newArea.set(null);
  }

  protected removeArea(id: string): Promise<void> {
    return this.run(() => this.api.removeArea(id), 'Area removed.');
  }

  protected async upload(event: Event): Promise<void> {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    input.value = '';
    if (!file) return;
    await this.run(() => this.api.policeCertificate(file), 'Certificate uploaded. Our team will check it.');
  }

  protected removeCertificate(): Promise<void> {
    return this.run(() => this.api.removePoliceCertificate(), 'Certificate removed.');
  }

  protected async addBlock(): Promise<void> {
    const from = fromLocalInput(this.offFrom());
    const to = fromLocalInput(this.offTo());
    if (!from || !to) return;
    await this.run(() => this.api.addBlock(from, to, this.offNote().trim() || null), 'Time off added.');
    this.offFrom.set('');
    this.offTo.set('');
    this.offNote.set('');
  }

  protected removeBlock(id: string): Promise<void> {
    return this.run(() => this.api.removeBlock(id), 'Time off removed.');
  }

  protected async leave(): Promise<void> {
    this.busy.set(true);
    try {
      await this.api.leave();
      this.toast.success('You’ve stopped being a driver partner.');
      void this.router.navigateByUrl('/drivers');
    } catch (e) {
      this.toast.error(problemOf(e).detail);
    } finally {
      this.busy.set(false);
      this.confirmLeave.set(false);
    }
  }

  private async run(call: () => Promise<MyDriverProfile>, done: string): Promise<void> {
    this.busy.set(true);
    try {
      this.fill(await call());
      this.toast.success(done);
    } catch (e) {
      const p = problemOf(e);
      this.toast.error(p.errors ? Object.values(p.errors)[0] : p.detail);
    } finally {
      this.busy.set(false);
    }
  }

  private fill(p: MyDriverProfile): void {
    this.profile.set(p);
    this.licenceClass.set(p.licenceClass);
    this.validUntil.set(p.licenceValidUntil);
    this.experience.set(p.experienceYears);
    this.languages.set(p.languages.join(', '));
    this.manual.set(p.manual);
    this.automatic.set(p.automatic);
    this.outstation.set(p.outstation);
    this.hourPrice.set(p.rates.hourPrice);
    this.dayPrice.set(p.rates.dayPrice);
    this.outstationPrice.set(p.rates.outstationPrice);
    this.allowance.set(p.rates.allowance);
    this.nightCharge.set(p.rates.nightCharge);
    this.about.set(p.about ?? '');
  }
}
