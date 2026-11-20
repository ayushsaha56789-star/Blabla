import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, inject, OnInit, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { DriversApi, TripView, tripLength, tripStatus } from '../../core/drivers-api';
import { problemOf } from '../../core/errors';
import { FeaturesService } from '../../core/features.service';
import { rupees } from '../../core/rides-format';
import { Avatar } from '../../shared/avatar';
import { Icon } from '../../shared/icon';

/** Driver trips: drivers I hired, and (for driver partners) trips I drive. */
@Component({
  selector: 'app-trips-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [DatePipe, RouterLink, Avatar, Icon],
  template: `
    <div class="container-page max-w-4xl py-8 sm:py-10">
      <header class="flex flex-wrap items-end justify-between gap-4">
        <div>
          <p class="kicker">Hire a driver</p>
          <h1 class="mt-1 text-3xl font-extrabold tracking-tight">Driver trips</h1>
        </div>
        @if (features.isOn('drivers.hire')) { <a routerLink="/drivers" class="btn btn-primary btn-sm"><app-icon name="search" [size]="16" /> Find a driver</a> }
      </header>
      @if (driving()?.length) {
        <div class="segmented mt-6 grid-cols-2" role="tablist" aria-label="Show">
          <button type="button" role="tab" class="segment" [class.segment-on]="tab() === 'hired'" [attr.aria-selected]="tab() === 'hired'" (click)="tab.set('hired')">Drivers I hired</button>
          <button type="button" role="tab" class="segment" [class.segment-on]="tab() === 'driving'" [attr.aria-selected]="tab() === 'driving'" (click)="tab.set('driving')">
            I'm driving@if (waiting()) { <span class="ml-1 rounded-full bg-accent-500 px-1.5 text-[0.7rem] leading-5 font-bold text-white">{{ waiting() }}</span> }
          </button>
        </div>
      }

      @if (error()) { <p class="card mt-6 p-6 text-center" role="alert">{{ error() }}</p> }
      @if (shown(); as list) {
        @if (!list.length) {
          <div class="card mt-6 p-10 text-center">
            <app-icon name="steering" [size]="32" class="text-slate-400" />
            <p class="mt-3 font-semibold">No driver trips yet.</p>
            <p class="mt-1 text-sm text-slate-600 dark:text-slate-400">Hire a checked driver for your own car: in town by the hour, or outstation by the day.</p>
            @if (features.isOn('drivers.hire')) { <a routerLink="/drivers" class="btn btn-primary mt-4">Find a driver</a> }
          </div>
        } @else {
          <ul class="mt-6 space-y-3">
            @for (t of list; track t.id) {
              <li>
                <a [routerLink]="['/drivers/trips', t.id]" class="card flex items-center gap-4 p-4 transition hover:shadow-md">
                  <app-avatar [url]="other(t).avatarUrl" [name]="other(t).displayName" [size]="48" />
                  <span class="min-w-0 flex-1">
                    <span class="block truncate font-semibold">{{ t.viewer === 'DRIVER' ? 'Driving for ' : 'Driver: ' }}{{ other(t).displayName }}</span>
                    <span class="block text-sm text-slate-500 dark:text-slate-400">{{ t.from | date: 'EEE d MMM, h:mm a' }} · {{ length(t) }} · {{ t.placeName }}@if (t.destinationName) { → {{ t.destinationName }} }</span>
                  </span>
                  <span class="flex flex-col items-end gap-1">
                    <span class="badge" [class]="'tone-' + status(t).tone">{{ status(t).text }}</span>
                    <span class="text-sm font-bold">{{ money(t.fare) }}</span>
                  </span>
                </a>
              </li>
            }
          </ul>
        }
      } @else if (!error()) { <div class="skeleton mt-6 h-24"></div> }
    </div>
  `,
})
export class TripsPage implements OnInit {
  private readonly api = inject(DriversApi);
  protected readonly features = inject(FeaturesService);

  protected readonly hired = signal<TripView[] | null>(null);
  protected readonly driving = signal<TripView[] | null>(null);
  protected readonly tab = signal<'hired' | 'driving'>('hired');
  protected readonly error = signal<string | null>(null);
  protected readonly money = rupees;
  protected readonly status = tripStatus;

  protected readonly shown = computed(() => this.tab() === 'driving' ? this.driving() : this.hired());
  protected readonly waiting = computed(() => (this.driving() ?? []).filter((t) => t.status === 'REQUESTED').length);

  async ngOnInit(): Promise<void> {
    try {
      const mine = await this.api.myTrips();
      this.hired.set(mine.hired);
      this.driving.set(mine.driving);
      if (!mine.hired.length && mine.driving.length) this.tab.set('driving');
      if (mine.driving.some((t) => t.status === 'REQUESTED')) this.tab.set('driving');
    } catch (e) {
      this.error.set(problemOf(e).detail);
    }
  }

  protected other(t: TripView) {
    return t.viewer === 'DRIVER' ? t.customer : t.driver;
  }

  protected length(t: TripView): string {
    return tripLength(t.kind, t.units);
  }
}
