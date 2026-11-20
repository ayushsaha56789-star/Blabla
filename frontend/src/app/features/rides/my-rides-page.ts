import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, effect, inject, signal, untracked } from '@angular/core';
import { RouterLink } from '@angular/router';
import { AuthService } from '../../core/auth.service';
import { problemOf } from '../../core/errors';
import { FeaturesService } from '../../core/features.service';
import { bookingStatus, rupees } from '../../core/rides-format';
import { Trips } from '../../core/rides.models';
import { RidesApi } from '../../core/rides-api';
import { TripsService } from '../../core/trips.service';
import { Avatar } from '../../shared/avatar';
import { Icon } from '../../shared/icon';
import { PendingReviews } from './pending-reviews';

/** My rides: trips I drive and seats I booked, upcoming or past. */
@Component({
  selector: 'app-my-rides-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [DatePipe, RouterLink, Icon, Avatar, PendingReviews],
  template: `
    <div class="container-page max-w-3xl py-8 sm:py-12">
      <div class="flex flex-wrap items-end justify-between gap-4">
        <div>
          <h1 class="text-3xl font-extrabold tracking-tight">My rides</h1>
          <p class="mt-1 text-slate-600 dark:text-slate-400">Trips you're driving and seats you've booked.</p>
        </div>
        <div class="segmented grid-cols-2" role="tablist" aria-label="Which trips">
          <button type="button" role="tab" class="segment px-5" [class.segment-on]="when() === 'upcoming'" [attr.aria-selected]="when() === 'upcoming'" (click)="when.set('upcoming')">Upcoming</button>
          <button type="button" role="tab" class="segment px-5" [class.segment-on]="when() === 'past'" [attr.aria-selected]="when() === 'past'" (click)="when.set('past')">Past</button>
        </div>
      </div>

      <app-pending-reviews />

      @if (error()) {
        <p class="card mt-6 p-6 text-center" role="alert">{{ error() }}</p>
      } @else if (!trips()) {
        @for (i of [1, 2]; track i) { <div class="card mt-4 p-5"><div class="skeleton h-5 w-1/2"></div><div class="skeleton mt-3 h-4 w-1/3"></div></div> }
      } @else if (empty()) {
        <section class="card mt-6 p-8 text-center sm:p-12">
          <span class="mx-auto flex h-16 w-16 items-center justify-center rounded-3xl bg-brand-50 text-brand-600 dark:bg-brand-950 dark:text-brand-400"><app-icon name="route" [size]="30" /></span>
          <h2 class="mt-5 text-xl font-bold">{{ when() === 'upcoming' ? 'No upcoming trips' : 'No past trips yet' }}</h2>
          <p class="mx-auto mt-2 max-w-sm text-slate-600 dark:text-slate-400">Find a ride for your next trip, or offer the empty seats in your car.</p>
          <div class="mt-6 flex flex-wrap justify-center gap-2">
            @if (features.isOn('rides.booking')) { <a routerLink="/search" class="btn btn-primary"><app-icon name="search" [size]="18" /> Find a ride</a> }
            @if (features.isOn('rides.publish')) { <a routerLink="/publish" class="btn btn-secondary"><app-icon name="plusCircle" [size]="18" /> Offer a ride</a> }
          </div>
        </section>
      } @else {
        @if (trips()!.driving.length) {
          <h2 class="kicker mt-8 mb-3">Driving</h2>
          <ul class="space-y-3">
            @for (t of trips()!.driving; track t.ride.id) {
              <li>
                <a [routerLink]="['/rides', t.ride.id]" class="card block p-5 transition hover:-translate-y-0.5 hover:shadow-lg hover:shadow-slate-900/5">
                  <div class="flex flex-wrap items-start justify-between gap-3">
                    <div class="min-w-0">
                      <p class="text-sm font-semibold text-slate-500 dark:text-slate-400">{{ t.ride.departureAt | date: 'EEE, d MMM · h:mm a' }}</p>
                      <p class="mt-1 text-lg font-bold">{{ t.ride.fromName }} → {{ t.ride.toName }}</p>
                      @if (t.ride.via.length) { <p class="text-sm text-slate-500 dark:text-slate-400">via {{ t.ride.via.join(', ') }}</p> }
                    </div>
                    <div class="flex flex-wrap justify-end gap-1.5">
                      @if (t.ride.status === 'CANCELLED') { <span class="badge tone-danger">Cancelled</span> }
                      @else if (t.pendingRequests) { <span class="badge tone-warning">{{ t.pendingRequests }} {{ t.pendingRequests === 1 ? 'request' : 'requests' }} waiting</span> }
                      <span class="badge tone-muted"><app-icon name="users" [size]="13" /> {{ t.seatsBooked }}/{{ t.ride.seatsTotal }} booked</span>
                    </div>
                  </div>
                </a>
              </li>
            }
          </ul>
        }
        @if (trips()!.riding.length) {
          <h2 class="kicker mt-8 mb-3">Riding</h2>
          <ul class="space-y-3">
            @for (t of trips()!.riding; track t.booking.id) {
              <li>
                <a [routerLink]="['/rides', t.ride.id]" class="card block p-5 transition hover:-translate-y-0.5 hover:shadow-lg hover:shadow-slate-900/5">
                  <div class="flex flex-wrap items-start justify-between gap-3">
                    <div class="min-w-0">
                      <p class="text-sm font-semibold text-slate-500 dark:text-slate-400">{{ t.booking.pickupAt | date: 'EEE, d MMM · h:mm a' }}</p>
                      <p class="mt-1 text-lg font-bold">{{ t.booking.fromName }} → {{ t.booking.toName }}</p>
                      <p class="mt-2 flex items-center gap-2 text-sm text-slate-600 dark:text-slate-400">
                        <app-avatar [url]="t.driver.avatarUrl" [name]="t.driver.displayName" [size]="24" /> with {{ t.driver.displayName }}
                        · {{ t.booking.seats }} {{ t.booking.seats === 1 ? 'seat' : 'seats' }} · {{ fmtRupees(t.booking.total) }}
                      </p>
                    </div>
                    <span class="badge" [class]="'tone-' + status(t.booking.status, t.booking.cancelledBy).tone">{{ status(t.booking.status, t.booking.cancelledBy).text }}</span>
                  </div>
                </a>
              </li>
            }
          </ul>
        }
      }
    </div>
  `,
})
export class MyRidesPage {
  private readonly api = inject(RidesApi);
  private readonly tripsBadge = inject(TripsService);
  protected readonly features = inject(FeaturesService);
  protected readonly auth = inject(AuthService);

  protected readonly when = signal<'upcoming' | 'past'>('upcoming');
  protected readonly trips = signal<Trips | null>(null);
  protected readonly error = signal<string | null>(null);
  protected readonly empty = computed(() => !this.trips()?.driving.length && !this.trips()?.riding.length);
  protected readonly fmtRupees = rupees;
  protected readonly status = bookingStatus;

  constructor() {
    effect(() => {
      const when = this.when();
      untracked(() => void this.load(when));
    });
  }

  private async load(when: 'upcoming' | 'past'): Promise<void> {
    this.trips.set(null);
    this.error.set(null);
    try {
      const t = await this.api.trips(when);
      if (this.when() === when) this.trips.set(t);
      void this.tripsBadge.refresh();
    } catch (e) {
      this.error.set(problemOf(e).detail);
    }
  }
}
