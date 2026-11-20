import { DatePipe, TitleCasePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, effect, inject, input, signal, untracked } from '@angular/core';
import { RouterLink } from '@angular/router';
import { problemOf } from '../../core/errors';
import { duration, rupees } from '../../core/rides-format';
import { BookingStatus } from '../../core/rides.models';
import { ToastService } from '../../core/toast.service';
import { Avatar } from '../../shared/avatar';
import { Icon } from '../../shared/icon';
import { AdminApi } from '../admin-api';
import { AdminRideDetails } from '../admin.models';
import { ConfirmationCancelled } from '../confirm-code.service';
import { ConsoleSessionService } from '../console-session.service';

/** One ride in the console: route, driver, every booking with its message, and cancelling the ride. */
@Component({
  selector: 'app-admin-ride-detail',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [DatePipe, TitleCasePipe, RouterLink, Avatar, Icon],
  template: `
    <a routerLink="/admin/rides" class="btn btn-ghost btn-sm -ml-2"><app-icon name="arrowLeft" [size]="16" /> All rides</a>
    @if (error()) {
      <p class="card mt-4 p-6 text-rose-600 dark:text-rose-400" role="alert">{{ error() }}</p>
    } @else if (data(); as d) {
      <header class="mt-3 flex flex-wrap items-start justify-between gap-3">
        <div>
          <p class="kicker">{{ d.ride.departureAt | date: 'EEEE, d MMMM y, h:mm a' }}</p>
          <h1 class="mt-1 text-2xl font-extrabold tracking-tight sm:text-3xl">{{ d.ride.stops[0].name }} → {{ d.ride.stops[d.ride.stops.length - 1].name }}</h1>
          <p class="mt-1 text-slate-600 dark:text-slate-400">About {{ d.ride.distanceKm }} km · {{ fmtDuration(d.ride.durationMin) }} · {{ fmtRupees(d.ride.pricePerSeat) }} per seat · {{ d.ride.seatsTotal }} seats</p>
        </div>
        <span class="flex flex-wrap gap-1.5">
          <span class="badge" [class]="d.ride.status === 'SCHEDULED' ? 'badge-success' : d.ride.status === 'CANCELLED' ? 'badge-danger' : 'badge-muted'">{{ d.ride.status | titlecase }}</span>
          @if (d.ride.ladiesOnly) { <span class="badge badge-muted">Ladies only</span> }
          @if (d.ride.instantBooking) { <span class="badge badge-muted">Instant booking</span> }
        </span>
      </header>

      @if (d.ride.status === 'CANCELLED') {
        <p class="mt-4 rounded-2xl bg-rose-50 p-4 text-sm text-rose-800 dark:bg-rose-500/10 dark:text-rose-200">
          Cancelled by {{ d.ride.cancelledBy === 'STAFF' ? 'staff' : 'the driver' }} on {{ d.ride.cancelledAt | date: 'd MMM, h:mm a' }}.
          @if (d.ride.cancelReason) { Reason: “{{ d.ride.cancelReason }}” }
        </p>
      }

      <div class="mt-6 grid gap-5 lg:grid-cols-2">
        <section class="card p-5">
          <h2 class="kicker mb-3">Route</h2>
          <ol>
            @for (s of d.ride.stops; track s.position; let lastStop = $last) {
              <li class="relative flex gap-3" [class.pb-4]="!lastStop">
                <span class="route-dot"></span>
                @if (!lastStop) { <span class="route-line"></span> }
                <span class="min-w-0">
                  <span class="block font-semibold">{{ s.name }} <span class="text-sm font-normal text-slate-500">{{ s.state !== s.name ? s.state + ' · ' : '' }}{{ s.at | date: 'h:mm a' }}</span></span>
                  @if (s.meetingPoint) { <span class="block text-sm text-slate-500 dark:text-slate-400">{{ s.meetingPoint }}</span> }
                </span>
              </li>
            }
          </ol>
        </section>
        <section class="card p-5">
          <h2 class="kicker mb-3">Driver</h2>
          <a [routerLink]="['/admin/members', d.ride.driver.profile.id]" class="flex items-center gap-3">
            <app-avatar [url]="d.ride.driver.profile.avatarUrl" [name]="d.ride.driver.profile.displayName" [size]="48" />
            <span>
              <span class="block font-bold">{{ d.ride.driver.profile.displayName }}</span>
              <span class="block text-sm text-slate-500 dark:text-slate-400">{{ d.ride.driver.ridesCompleted }} rides completed · open member page</span>
            </span>
          </a>
          @if (d.ride.car; as car) { <p class="mt-4 text-sm"><app-icon name="car" [size]="16" class="mr-1 align-[-3px] text-slate-400" />{{ car.make }} {{ car.model }}{{ car.color ? ' · ' + car.color : '' }}</p> }
          @if (d.ride.notes) { <p class="mt-3 rounded-2xl bg-slate-50 p-3 text-sm dark:bg-slate-800/60">“{{ d.ride.notes }}”</p> }
        </section>
      </div>

      <section class="card mt-5 p-5">
        <h2 class="kicker mb-3">Bookings ({{ d.bookings.length }})</h2>
        @if (d.bookings.length) {
          <ul class="divide-y divide-slate-100 dark:divide-slate-800">
            @for (b of d.bookings; track b.bookingId) {
              <li class="flex flex-wrap items-start gap-3 py-3">
                <a [routerLink]="['/admin/members', b.member.id]"><app-avatar [url]="b.member.avatarUrl" [name]="b.member.displayName" [size]="36" /></a>
                <span class="min-w-0 flex-1">
                  <a [routerLink]="['/admin/members', b.member.id]" class="font-semibold hover:underline">{{ b.member.displayName }}</a>
                  <span class="block text-sm text-slate-500 dark:text-slate-400">
                    {{ d.ride.stops[b.fromPosition].name }} → {{ d.ride.stops[b.toPosition].name }} · {{ b.seats }} {{ b.seats === 1 ? 'seat' : 'seats' }} · {{ fmtRupees(b.seats * b.pricePerSeat) }} · {{ b.requestedAt | date: 'd MMM, h:mm a' }}
                  </span>
                  @if (b.message) { <span class="mt-1 block text-sm">“{{ b.message }}”</span> }
                </span>
                <span class="badge" [class]="'tone-' + status(b.status, b.cancelledBy).tone">{{ status(b.status, b.cancelledBy).text }}</span>
              </li>
            }
          </ul>
        } @else {
          <p class="text-sm text-slate-500">No bookings.</p>
        }
      </section>

      @if (d.ride.status === 'SCHEDULED' && session.can('content.moderate')) {
        <section class="card mt-5 border-rose-200 p-5 dark:border-rose-500/30">
          <h2 class="font-bold">Cancel this ride</h2>
          <p class="mt-1 text-sm text-slate-600 dark:text-slate-400">For safety reports or rule breaks. Every booking is cancelled too, and the driver and passengers see your reason.</p>
          <label for="reason" class="label mt-4">Reason</label>
          <textarea id="reason" class="input min-h-20" maxlength="300" [value]="reason()" (input)="reason.set($any($event.target).value)" placeholder="e.g. Safety report under review"></textarea>
          <button type="button" class="btn mt-3 bg-rose-600 text-white hover:bg-rose-700" (click)="cancel()" [disabled]="busy() || reason().trim().length < 3">
            <app-icon name="ban" [size]="18" /> Cancel ride
          </button>
        </section>
      }
    } @else {
      <div class="skeleton mt-4 h-64"></div>
    }
  `,
})
export class RideDetailPage {
  private readonly api = inject(AdminApi);
  private readonly toast = inject(ToastService);
  protected readonly session = inject(ConsoleSessionService);

  readonly id = input.required<string>();

  protected readonly data = signal<AdminRideDetails | null>(null);
  protected readonly error = signal<string | null>(null);
  protected readonly reason = signal('');
  protected readonly busy = signal(false);
  protected readonly fmtRupees = rupees;
  protected readonly fmtDuration = duration;

  constructor() {
    effect(() => {
      const id = this.id();
      untracked(() => void this.load(id));
    });
  }

  /** Booking states as staff read them (the member-facing labels speak to the passenger). */
  protected status(s: BookingStatus, cancelledBy: string | null): { text: string; tone: string } {
    const who: Record<string, string> = { PASSENGER: 'the passenger', DRIVER: 'the driver', STAFF: 'staff', SYSTEM: 'SaathChalo' };
    switch (s) {
      case 'AWAITING_PAYMENT': return { text: 'Paying online', tone: 'warning' };
      case 'REQUESTED': return { text: 'Requested', tone: 'warning' };
      case 'CONFIRMED': return { text: 'Confirmed', tone: 'success' };
      case 'DECLINED': return { text: 'Declined by the driver', tone: 'danger' };
      case 'EXPIRED': return { text: 'Expired (no answer)', tone: 'muted' };
      case 'COMPLETED': return { text: 'Completed', tone: 'muted' };
      case 'CANCELLED': return { text: `Cancelled by ${who[cancelledBy ?? ''] ?? 'someone'}`, tone: 'danger' };
    }
  }

  protected async cancel(): Promise<void> {
    this.busy.set(true);
    try {
      this.data.set(await this.api.cancelRide(this.id(), this.reason().trim()));
      this.reason.set('');
      this.toast.success('Ride cancelled. The driver and passengers can see it.');
    } catch (e) {
      if (!(e instanceof ConfirmationCancelled)) this.toast.error(problemOf(e).detail);
    } finally {
      this.busy.set(false);
    }
  }

  private async load(id: string): Promise<void> {
    try {
      this.data.set(await this.api.ride(id));
    } catch (e) {
      this.error.set(problemOf(e).detail);
    }
  }
}
