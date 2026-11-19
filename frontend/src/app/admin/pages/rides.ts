import { DatePipe, DecimalPipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, DestroyRef, inject, OnInit, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { problemOf } from '../../core/errors';
import { rupees } from '../../core/rides-format';
import { Avatar } from '../../shared/avatar';
import { Icon } from '../../shared/icon';
import { AdminApi } from '../admin-api';
import { AdminRideRow, PageResult } from '../admin.models';

type Show = 'upcoming' | 'past' | 'cancelled' | 'all';

/** All rides on SaathChalo, searchable by any town on the route. */
@Component({
  selector: 'app-admin-rides',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, DatePipe, DecimalPipe, Avatar, Icon],
  template: `
    <header>
      <p class="kicker">Carpooling</p>
      <h1 class="mt-1 text-3xl font-extrabold tracking-tight">Rides</h1>
      @if (result(); as r) { <p class="mt-1 text-slate-600 dark:text-slate-400">{{ r.total | number }} {{ show() === 'all' ? 'in total' : show() }}</p> }
    </header>

    <div class="mt-6 flex flex-col gap-3 sm:flex-row">
      <div class="segmented grid-cols-4 sm:w-auto" role="tablist" aria-label="Which rides">
        @for (s of shows; track s.key) {
          <button type="button" role="tab" class="segment" [class.segment-on]="show() === s.key" [attr.aria-selected]="show() === s.key" (click)="setShow(s.key)">{{ s.label }}</button>
        }
      </div>
      <label class="relative block flex-1">
        <span class="sr-only">Search rides by town</span>
        <app-icon name="search" class="pointer-events-none absolute top-1/2 left-4 -translate-y-1/2 text-slate-400" [size]="18" />
        <input class="input pl-11" type="search" placeholder="Search by any town on the route…" autocomplete="off" [value]="query()" (input)="onSearch($any($event.target).value)" />
      </label>
    </div>

    @if (error()) { <p class="field-error mt-4" role="alert">{{ error() }}</p> }

    @if (result(); as r) {
      <div class="card mt-5 overflow-hidden transition-opacity" [class.opacity-60]="loading()">
        @if (!r.items.length) {
          <div class="p-10 text-center">
            <app-icon name="car" [size]="32" class="text-slate-400" />
            <p class="mt-3 font-semibold">No rides here</p>
            <p class="text-sm text-slate-500">{{ query() ? 'Try another town.' : 'Rides members offer will show up here.' }}</p>
          </div>
        } @else {
          <div class="hidden overflow-x-auto md:block">
            <table class="data-table">
              <thead><tr><th>Departure</th><th>Route</th><th>Driver</th><th>Seats</th><th>Price</th><th>Status</th></tr></thead>
              <tbody>
                @for (ride of r.items; track ride.id) {
                  <tr class="cursor-pointer" [routerLink]="['/admin/rides', ride.id]">
                    <td class="whitespace-nowrap">
                      <a [routerLink]="['/admin/rides', ride.id]" class="font-semibold">{{ ride.departureAt | date: 'd MMM, h:mm a' }}</a>
                    </td>
                    <td>
                      <span class="font-semibold">{{ ride.fromName }} → {{ ride.toName }}</span>
                      @if (ride.stopCount > 2) { <span class="block text-xs text-slate-500">{{ ride.stopCount - 2 }} {{ ride.stopCount === 3 ? 'stop' : 'stops' }} on the way</span> }
                    </td>
                    <td><span class="flex items-center gap-2"><app-avatar [url]="ride.driver.avatarUrl" [name]="ride.driver.displayName" [size]="28" /> {{ ride.driver.displayName }}</span></td>
                    <td class="tabular-nums">{{ ride.seatsBooked }}/{{ ride.seatsTotal }} @if (ride.pendingRequests) { <span class="badge badge-warning ml-1 px-2 py-0">{{ ride.pendingRequests }} waiting</span> }</td>
                    <td class="tabular-nums">{{ fmtRupees(ride.pricePerSeat) }}</td>
                    <td>
                      <span class="flex flex-wrap gap-1">
                        <span class="badge" [class]="statusClass(ride.status)">{{ statusLabel(ride.status) }}</span>
                        @if (ride.ladiesOnly) { <span class="badge badge-muted">Ladies only</span> }
                      </span>
                    </td>
                  </tr>
                }
              </tbody>
            </table>
          </div>
          <ul class="divide-y divide-slate-100 md:hidden dark:divide-slate-800">
            @for (ride of r.items; track ride.id) {
              <li>
                <a [routerLink]="['/admin/rides', ride.id]" class="block p-4 active:bg-slate-50 dark:active:bg-slate-800/50">
                  <span class="flex items-center justify-between gap-2">
                    <span class="font-semibold">{{ ride.fromName }} → {{ ride.toName }}</span>
                    <span class="badge" [class]="statusClass(ride.status)">{{ statusLabel(ride.status) }}</span>
                  </span>
                  <span class="mt-1 block text-sm text-slate-500 dark:text-slate-400">{{ ride.departureAt | date: 'd MMM, h:mm a' }} · {{ ride.driver.displayName }} · {{ ride.seatsBooked }}/{{ ride.seatsTotal }} seats</span>
                </a>
              </li>
            }
          </ul>
          @if (r.total > r.size) {
            <div class="flex items-center justify-between border-t border-slate-100 p-3 text-sm dark:border-slate-800">
              <button type="button" class="btn btn-ghost btn-sm" (click)="go(-1)" [disabled]="page() === 0"><app-icon name="arrowLeft" [size]="16" /> Previous</button>
              <span class="text-slate-500">Page {{ page() + 1 }} of {{ pages(r) }}</span>
              <button type="button" class="btn btn-ghost btn-sm" (click)="go(1)" [disabled]="page() + 1 >= pages(r)">Next <app-icon name="arrowRight" [size]="16" /></button>
            </div>
          }
        }
      </div>
    } @else if (!error()) {
      <div class="skeleton mt-5 h-64"></div>
    }
  `,
})
export class RidesPage implements OnInit {
  private readonly api = inject(AdminApi);

  protected readonly shows: ReadonlyArray<{ key: Show; label: string }> = [
    { key: 'upcoming', label: 'Upcoming' },
    { key: 'past', label: 'Past' },
    { key: 'cancelled', label: 'Cancelled' },
    { key: 'all', label: 'All' },
  ];
  protected readonly show = signal<Show>('upcoming');
  protected readonly query = signal('');
  protected readonly page = signal(0);
  protected readonly result = signal<PageResult<AdminRideRow> | null>(null);
  protected readonly loading = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly fmtRupees = rupees;
  private debounce: ReturnType<typeof setTimeout> | undefined;

  constructor() {
    inject(DestroyRef).onDestroy(() => clearTimeout(this.debounce));
  }

  ngOnInit(): void {
    void this.load();
  }

  protected setShow(show: Show): void {
    this.show.set(show);
    this.page.set(0);
    void this.load();
  }

  protected onSearch(value: string): void {
    this.query.set(value);
    clearTimeout(this.debounce);
    this.debounce = setTimeout(() => {
      this.page.set(0);
      void this.load();
    }, 300);
  }

  protected go(delta: number): void {
    this.page.update((p) => Math.max(0, p + delta));
    void this.load();
  }

  protected pages(r: PageResult<AdminRideRow>): number {
    return Math.max(1, Math.ceil(r.total / r.size));
  }

  protected statusLabel(status: string): string {
    return status === 'SCHEDULED' ? 'Scheduled' : status === 'CANCELLED' ? 'Cancelled' : 'Completed';
  }

  protected statusClass(status: string): string {
    return status === 'SCHEDULED' ? 'badge-success' : status === 'CANCELLED' ? 'badge-danger' : 'badge-muted';
  }

  private async load(): Promise<void> {
    this.loading.set(true);
    this.error.set(null);
    try {
      this.result.set(await this.api.rides(this.show(), this.query().trim(), this.page()));
    } catch (e) {
      this.error.set(problemOf(e).detail);
    } finally {
      this.loading.set(false);
    }
  }
}
