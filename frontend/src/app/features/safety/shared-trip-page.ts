import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, DestroyRef, effect, inject, input, signal, untracked } from '@angular/core';
import { problemOf } from '../../core/errors';
import { SafetyApi, SharedTrip } from '../../core/safety-api';
import { Avatar } from '../../shared/avatar';
import { Icon } from '../../shared/icon';
import { Stars } from '../../shared/stars';

/** /trip/:token — what family see when a member shares their trip. No account needed; refreshes by itself. */
@Component({
  selector: 'app-shared-trip-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [DatePipe, Icon, Avatar, Stars],
  template: `
    <div class="container-page max-w-2xl py-8 sm:py-12">
      @if (error()) {
        <section class="card p-8 text-center" role="alert">
          <span class="mx-auto flex h-14 w-14 items-center justify-center rounded-3xl bg-slate-100 text-slate-500 dark:bg-slate-800"><app-icon name="route" [size]="26" /></span>
          <h1 class="mt-4 text-xl font-bold">{{ error() }}</h1>
          <p class="mt-2 text-sm text-slate-500 dark:text-slate-400">Trip links stop working a day after the trip, or when the traveller turns them off.</p>
        </section>
      } @else if (trip(); as t) {
        @if (t.sosAt) {
          <div class="mb-5 rounded-3xl bg-rose-600 p-5 text-white shadow-lg shadow-rose-600/30" role="alert">
            <p class="text-lg font-extrabold">{{ t.sharerName }} pressed SOS at {{ t.sosAt | date: 'h:mm a' }}</p>
            <p class="mt-1 text-rose-50">SaathChalo support has been alerted. Try calling them now. If you can't reach them and you're worried, call the police.</p>
            <a href="tel:112" class="btn mt-4 bg-white text-rose-700 hover:bg-rose-50"><app-icon name="phone" [size]="18" /> Call 112</a>
          </div>
        }
        <header>
          <p class="kicker">Shared trip</p>
          <h1 class="mt-1 text-2xl font-extrabold tracking-tight sm:text-3xl">{{ t.sharerName }} is travelling with SaathChalo</h1>
          <p class="mt-1 text-slate-600 dark:text-slate-400">
            {{ t.sharerIsDriver ? 'Driving' : 'Riding' }} {{ t.ride.fromName }} → {{ t.ride.toName }} · {{ t.ride.departureAt | date: 'EEE d MMM, h:mm a' }}
            @if (t.ride.status === 'CANCELLED') { · <b class="text-rose-600">cancelled</b> }
          </p>
        </header>

        <section class="card mt-5 p-5 sm:p-6" aria-labelledby="where-heading">
          <h2 id="where-heading" class="kicker mb-3">Where they are</h2>
          @if (t.live; as live) {
            <p class="text-lg font-bold">{{ live.near ? 'Near ' + live.near : 'Location shared' }}</p>
            <p class="text-sm text-slate-500 dark:text-slate-400">Updated {{ live.at | date: 'h:mm a' }}@if (live.accuracy) { · accurate to about {{ live.accuracy }} m}</p>
            <a class="btn btn-secondary mt-3" [href]="'https://www.google.com/maps?q=' + live.lat + ',' + live.lng" target="_blank" rel="noopener noreferrer">
              <app-icon name="pin" [size]="18" /> Open in Maps
            </a>
          } @else {
            <p class="text-sm text-slate-600 dark:text-slate-400">{{ t.sharerName }} hasn't turned on live location. The planned times are below.</p>
          }
        </section>

        <section class="card mt-5 p-5 sm:p-6" aria-labelledby="plan-heading">
          <h2 id="plan-heading" class="kicker mb-4">Route and planned times</h2>
          <ol>
            @for (s of t.stops; track s.position; let lastStop = $last) {
              <li class="flex gap-4">
                <time class="w-[4.75rem] shrink-0 pt-0.5 text-right text-sm font-bold whitespace-nowrap">{{ s.at | date: 'h:mm a' }}</time>
                <div class="relative flex min-w-0 flex-1 gap-3" [class.pb-5]="!lastStop">
                  <span class="route-dot"></span>
                  @if (!lastStop) { <span class="route-line"></span> }
                  <span class="min-w-0"><span class="block font-semibold">{{ s.name }}</span>
                    @if (s.meetingPoint) { <span class="block text-sm text-slate-500 dark:text-slate-400">{{ s.meetingPoint }}</span> }</span>
                </div>
              </li>
            }
          </ol>
        </section>

        <section class="card mt-5 p-5 sm:p-6" aria-labelledby="car-heading">
          <h2 id="car-heading" class="kicker mb-4">Driver and car</h2>
          <div class="flex items-center gap-3">
            <app-avatar [url]="t.driver.avatarUrl" [name]="t.driver.displayName" [size]="48" />
            <span><span class="block font-bold">{{ t.driver.displayName }}</span>
              <span class="block text-sm text-slate-500 dark:text-slate-400">
                @if (t.driver.rating != null) { <app-stars [value]="t.driver.rating" [count]="t.driver.reviewCount" /> }
                @if (t.driver.phoneVerified) { <span>{{ t.driver.rating != null ? ' · ' : '' }}Phone verified</span> }
              </span></span>
          </div>
          @if (t.car; as car) {
            <div class="mt-4 flex flex-wrap items-center gap-3">
              <app-icon name="car" [size]="20" class="text-slate-400" />
              <span class="font-semibold">{{ car.make }} {{ car.model }}@if (car.color) { · {{ car.color }}}</span>
              @if (car.plate) { <span class="rounded-lg border-2 border-slate-900 bg-white px-2 py-0.5 font-mono text-sm font-bold tracking-wider text-slate-900 dark:border-slate-200">{{ car.plate }}</span> }
            </div>
          }
        </section>
        <p class="mt-5 text-center text-xs text-slate-500 dark:text-slate-400">This page refreshes by itself. The link stops working {{ t.expiresAt | date: 'd MMM, h:mm a' }}.</p>
      } @else {
        <div class="card p-8" aria-busy="true"><div class="skeleton h-7 w-2/3"></div><div class="skeleton mt-4 h-4 w-1/2"></div></div>
      }
    </div>
  `,
})
export class SharedTripPage {
  private readonly api = inject(SafetyApi);

  readonly token = input.required<string>();

  protected readonly trip = signal<SharedTrip | null>(null);
  protected readonly error = signal<string | null>(null);

  constructor() {
    effect(() => {
      const token = this.token();
      untracked(() => void this.load(token));
    });
    const timer = setInterval(() => {
      if (document.visibilityState === 'visible' && this.trip()) void this.load(this.token());
    }, 30_000);
    inject(DestroyRef).onDestroy(() => clearInterval(timer));
  }

  private async load(token: string): Promise<void> {
    try {
      this.trip.set(await this.api.sharedTrip(token));
      this.error.set(null);
    } catch (e) {
      const p = problemOf(e);
      this.trip.set(null);
      this.error.set(p.status === 404 ? 'This trip link has expired' : p.detail);
    }
  }
}
