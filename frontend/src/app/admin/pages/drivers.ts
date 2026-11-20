import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, inject, OnInit, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { AdminDriverRow, LICENCE_LABELS, tripLength, tripStatus, TripView } from '../../core/drivers-api';
import { problemOf } from '../../core/errors';
import { rupees } from '../../core/rides-format';
import { ToastService } from '../../core/toast.service';
import { AuthImage } from '../../shared/auth-image';
import { Avatar } from '../../shared/avatar';
import { Icon } from '../../shared/icon';
import { AdminApi } from '../admin-api';

type Tab = 'review' | 'all' | 'trips';

const STATUS: Record<string, { text: string; tone: string }> = {
  DRAFT: { text: 'Not sent', tone: 'muted' },
  IN_REVIEW: { text: 'Waiting', tone: 'warning' },
  LIVE: { text: 'Live', tone: 'success' },
  REJECTED: { text: 'Sent back', tone: 'danger' },
  REMOVED: { text: 'Left', tone: 'muted' },
};

/** Driver partners: check new drivers, take one off the road, and look after hired trips. */
@Component({
  selector: 'app-admin-drivers',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [DatePipe, RouterLink, AuthImage, Avatar, Icon],
  template: `
    <header class="flex flex-wrap items-end justify-between gap-4">
      <div>
        <p class="kicker">Driver partners</p>
        <h1 class="mt-1 text-3xl font-extrabold tracking-tight">Drivers</h1>
        <p class="mt-1 text-slate-600 dark:text-slate-400">Approve a driver once their driving licence check is approved and the details match.</p>
      </div>
      <div class="segmented grid-cols-3" role="tablist" aria-label="Show">
        @for (t of tabs; track t.key) {
          <button type="button" role="tab" class="segment px-3 sm:px-4" [class.segment-on]="tab() === t.key" [attr.aria-selected]="tab() === t.key" (click)="show(t.key)">{{ t.label }}</button>
        }
      </div>
    </header>

    @if (error()) { <p class="field-error mt-4" role="alert">{{ error() }}</p> }

    @if (tab() !== 'trips') {
      @if (drivers(); as list) {
        @if (!list.length) {
          <div class="card mt-6 p-10 text-center"><app-icon name="checkCircle" [size]="32" class="text-brand-600" />
            <p class="mt-3 font-semibold">{{ tab() === 'review' ? 'No drivers waiting to be checked.' : 'No driver partners yet.' }}</p></div>
        } @else {
          <ul class="mt-6 space-y-4">
            @for (d of list; track d.id) {
              <li class="card p-5">
                <div class="flex flex-wrap items-start gap-4">
                  <app-avatar [url]="d.member.avatarUrl" [name]="d.member.displayName" [size]="56" />
                  <div class="min-w-0 flex-1">
                    <p class="flex flex-wrap items-center gap-2">
                      <a [routerLink]="['/admin/members', d.id]" class="font-bold hover:underline">{{ d.member.displayName }}</a>
                      <span class="badge" [class]="'tone-' + status(d.status).tone">{{ status(d.status).text }}</span>
                      @if (d.status === 'LIVE' && !d.takingBookings) { <span class="badge badge-muted">Not taking bookings</span> }
                    </p>
                    <p class="text-sm text-slate-600 dark:text-slate-400">{{ d.experienceYears }} years driving · {{ d.languages.join(', ') || 'no languages given' }} ·
                      {{ d.manual && d.automatic ? 'manual + automatic' : d.automatic ? 'automatic only' : 'manual only' }}@if (d.outstation) { · outstation }</p>
                    <p class="text-sm text-slate-600 dark:text-slate-400">{{ money(d.rates.hourPrice) }}/h · {{ money(d.rates.dayPrice) }}/day@if (d.outstation) { · outstation {{ money(d.rates.outstationPrice) }}/day + {{ money(d.rates.allowance) }} } · night +{{ money(d.rates.nightCharge) }}</p>
                    <p class="text-sm text-slate-600 dark:text-slate-400">Areas: @for (a of d.areas; track a.id) { {{ a.placeName }} ({{ a.radiusKm }} km)@if (!$last) {, } } @empty { none }</p>
                    @if (d.tripsDone) { <p class="text-sm text-slate-600 dark:text-slate-400">{{ d.tripsDone }} trips@if (d.rating) { · ★ {{ d.rating }} } </p> }
                    @if (d.about) { <p class="mt-1 text-sm italic text-slate-500 dark:text-slate-400">“{{ d.about }}”</p> }
                  </div>
                  <span class="text-xs text-slate-500">{{ d.updatedAt | date: 'd MMM, h:mm a' }}</span>
                </div>

                <div class="mt-4 grid gap-3 rounded-2xl bg-slate-50 p-4 text-sm sm:grid-cols-2 dark:bg-slate-800/60">
                  <div>
                    <p class="font-semibold">Driving licence</p>
                    <p class="mt-1">{{ licenceLabels[d.licenceClass] }}, valid until {{ d.licenceValidUntil | date: 'd MMM y' }}</p>
                    <p class="mt-1 flex flex-wrap items-center gap-2">Number: <span class="font-mono">{{ numbers()[d.id] ?? d.licenceNumberMasked }}</span>
                      @if (!numbers()[d.id]) { <button type="button" class="btn btn-ghost btn-sm" (click)="reveal(d)"><app-icon name="eye" [size]="14" /> Show (recorded)</button> }</p>
                    <p class="mt-1">Licence check:
                      @switch (d.licenceStatus) {
                        @case ('APPROVED') { <span class="badge badge-success">Approved</span> }
                        @case ('PENDING') { <span class="badge badge-warning">Waiting</span> · <a routerLink="/admin/verifications" class="font-semibold text-brand-700 underline dark:text-brand-400">Check it in ID checks</a> }
                        @default { <span class="badge badge-danger">{{ d.licenceStatus === 'REJECTED' ? 'Rejected' : 'Not sent' }}</span> }
                      }</p>
                  </div>
                  <div>
                    <p class="font-semibold">Police verification</p>
                    @if (d.policeCertificate) {
                      <app-auth-img [src]="'/api/admin/drivers/' + d.id + '/police-certificate'" alt="Police verification certificate" class="mt-2 h-40 w-full" fit="contain" />
                      <p class="mt-1 text-xs text-slate-500 dark:text-slate-400">{{ d.policeChecked ? 'Checked' : 'Not checked yet' }} · viewing is recorded</p>
                    } @else { <p class="mt-1 text-slate-500 dark:text-slate-400">Not added (optional).</p> }
                  </div>
                </div>

                @if (d.reviewNote) { <p class="mt-3 text-sm"><b>Last note:</b> {{ d.reviewNote }}</p> }

                @if (d.status === 'IN_REVIEW' || d.status === 'LIVE') {
                  <div class="mt-4 flex flex-wrap items-end gap-2 border-t border-slate-100 pt-4 dark:border-slate-800">
                    @if (d.status === 'IN_REVIEW') {
                      @if (d.policeCertificate) {
                        <label class="flex items-center gap-2 text-sm"><input type="checkbox" class="h-4 w-4 accent-brand-600" [checked]="police()[d.id]" (change)="setPolice(d.id, $any($event.target).checked)" /> Police certificate checked</label>
                      }
                      <button type="button" class="btn btn-primary btn-sm" (click)="approve(d)" [disabled]="busy() || d.licenceStatus !== 'APPROVED'"><app-icon name="check" [size]="16" /> Approve</button>
                    } @else if (d.policeCertificate && !d.policeChecked) {
                      <button type="button" class="btn btn-secondary btn-sm" (click)="policeChecked(d)" [disabled]="busy()">Police certificate checked</button>
                    }
                    <label class="min-w-56 flex-1 text-sm"><span class="label text-xs">{{ d.status === 'LIVE' ? 'Take off the road: why?' : 'Or send it back: what should they fix?' }}</span>
                      <input class="input py-2 text-sm" maxlength="300" [value]="notes()[d.id] ?? ''" (input)="note(d.id, $any($event.target).value)" [placeholder]="d.status === 'LIVE' ? 'Safety report under review' : 'The licence number doesn’t match the photo'" /></label>
                    <button type="button" class="btn btn-ghost btn-sm text-rose-600 dark:text-rose-400" (click)="reject(d)" [disabled]="busy() || (notes()[d.id] ?? '').trim().length < 3">{{ d.status === 'LIVE' ? 'Take off' : 'Send back' }}</button>
                  </div>
                  @if (d.status === 'LIVE') { <p class="hint">Taking a driver off cancels their upcoming trips; members get everything back.</p> }
                }
              </li>
            }
          </ul>
        }
      } @else if (!error()) { <div class="skeleton mt-6 h-40"></div> }
    } @else {
      @if (trips(); as list) {
        @if (!list.length) {
          <div class="card mt-6 p-10 text-center"><app-icon name="steering" [size]="32" class="text-slate-400" /><p class="mt-3 font-semibold">No hired-driver trips yet.</p></div>
        } @else {
          <ul class="mt-6 space-y-3">
            @for (t of list; track t.id) {
              <li class="card p-4 text-sm">
                <div class="flex flex-wrap items-start justify-between gap-2">
                  <div class="min-w-0">
                    <p><span class="font-bold">{{ t.driver.displayName }}</span> driving for <span class="font-bold">{{ t.customer.displayName }}</span></p>
                    <p class="text-slate-600 dark:text-slate-400">{{ t.from | date: 'EEE d MMM, h:mm a' }} · {{ length(t) }} · {{ t.placeName }}@if (t.destinationName) { → {{ t.destinationName }} } · {{ money(t.fare) }}{{ t.payOnline ? ' online' : ' direct' }}</p>
                    @if (t.cancelReason) { <p class="text-slate-500 dark:text-slate-400">“{{ t.cancelReason }}”</p> }
                  </div>
                  <span class="badge" [class]="'tone-' + tripState(t).tone">{{ tripState(t).text }}</span>
                </div>
                @if (['REQUESTED', 'ACCEPTED', 'PAID', 'ON_THE_WAY'].includes(t.status)) {
                  <div class="mt-3 flex flex-wrap items-end gap-2">
                    <input class="input min-w-48 flex-1 py-2 text-sm" maxlength="300" [value]="notes()[t.id] ?? ''" (input)="note(t.id, $any($event.target).value)" placeholder="Reason (members see it)" />
                    <button type="button" class="btn btn-ghost btn-sm text-rose-600 dark:text-rose-400" (click)="cancelTrip(t)" [disabled]="busy()">Cancel and refund</button>
                  </div>
                }
              </li>
            }
          </ul>
        }
      } @else if (!error()) { <div class="skeleton mt-6 h-40"></div> }
    }
  `,
})
export class DriversAdminPage implements OnInit {
  private readonly api = inject(AdminApi);
  private readonly toast = inject(ToastService);

  protected readonly tabs: { key: Tab; label: string }[] = [
    { key: 'review', label: 'To check' }, { key: 'all', label: 'All drivers' }, { key: 'trips', label: 'Trips' },
  ];
  protected readonly tab = signal<Tab>('review');
  protected readonly drivers = signal<AdminDriverRow[] | null>(null);
  protected readonly trips = signal<TripView[] | null>(null);
  protected readonly notes = signal<Record<string, string>>({});
  protected readonly police = signal<Record<string, boolean>>({});
  protected readonly numbers = signal<Record<string, string>>({});
  protected readonly busy = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly money = rupees;
  protected readonly licenceLabels = LICENCE_LABELS;
  protected readonly tripState = tripStatus;

  ngOnInit(): void {
    void this.load();
  }

  protected status(s: string) {
    return STATUS[s] ?? { text: s, tone: 'muted' };
  }

  protected length(t: TripView): string {
    return tripLength(t.kind, t.units);
  }

  protected show(t: Tab): void {
    this.tab.set(t);
    this.drivers.set(null);
    this.trips.set(null);
    void this.load();
  }

  protected note(id: string, value: string): void {
    this.notes.update((n) => ({ ...n, [id]: value }));
  }

  protected setPolice(id: string, on: boolean): void {
    this.police.update((p) => ({ ...p, [id]: on }));
  }

  protected async reveal(d: AdminDriverRow): Promise<void> {
    try {
      const { number } = await this.api.driverLicenceNumber(d.id);
      if (number) this.numbers.update((n) => ({ ...n, [d.id]: number }));
    } catch (e) {
      this.toast.error(problemOf(e).detail);
    }
  }

  protected approve(d: AdminDriverRow): Promise<void> {
    return this.act(() => this.api.approveDriver(d.id, !!this.police()[d.id]), `${d.member.displayName} is live: members can book them now.`);
  }

  protected reject(d: AdminDriverRow): Promise<void> {
    const live = d.status === 'LIVE';
    return this.act(() => this.api.rejectDriver(d.id, (this.notes()[d.id] ?? '').trim()),
      live ? 'Taken off the road. Their upcoming trips were cancelled and refunded.' : 'Sent back to the driver.');
  }

  protected policeChecked(d: AdminDriverRow): Promise<void> {
    return this.act(() => this.api.driverPoliceChecked(d.id), 'Marked as police verified.');
  }

  protected cancelTrip(t: TripView): Promise<void> {
    return this.act(() => this.api.cancelDriverTrip(t.id, (this.notes()[t.id] ?? '').trim() || null), 'Cancelled. The member gets everything back.');
  }

  private async act(call: () => Promise<unknown>, done: string): Promise<void> {
    this.busy.set(true);
    try {
      await call();
      this.toast.success(done);
      await this.load();
    } catch (e) {
      this.toast.error(problemOf(e).detail);
    } finally {
      this.busy.set(false);
    }
  }

  private async load(): Promise<void> {
    try {
      if (this.tab() === 'trips') this.trips.set(await this.api.driverTrips());
      else this.drivers.set(await this.api.drivers(this.tab() === 'review' ? 'IN_REVIEW' : 'ALL'));
      this.error.set(null);
    } catch (e) {
      this.error.set(problemOf(e).detail);
    }
  }
}
