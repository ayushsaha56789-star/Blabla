import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, effect, inject, input, signal, untracked } from '@angular/core';
import { RouterLink } from '@angular/router';
import { problemOf } from '../../core/errors';
import { FeaturesService } from '../../core/features.service';
import { EventCard, RideCard, SocialApi } from '../../core/social-api';
import { Avatar } from '../../shared/avatar';
import { Icon } from '../../shared/icon';

/** An event and every ride going there; offer one if there aren't enough. */
@Component({
  selector: 'app-event-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [DatePipe, RouterLink, Avatar, Icon],
  template: `
    <div class="container-page max-w-3xl py-6 sm:py-10">
      <a routerLink="/community" class="btn btn-ghost btn-sm -ml-2"><app-icon name="arrowLeft" [size]="16" /> Squads &amp; events</a>
      @if (error()) {
        <p class="card mt-4 p-6 text-center" role="alert">{{ error() }}</p>
      } @else if (data(); as d) {
        <header class="mt-3 overflow-hidden rounded-3xl bg-linear-to-br from-rose-500 to-fuchsia-600 p-6 text-white shadow-xl">
          <p class="text-sm font-semibold tracking-wider uppercase opacity-90">{{ d.event.startsAt | date: 'EEEE d MMMM · h:mm a' }}</p>
          <h1 class="mt-1 text-3xl font-extrabold">{{ d.event.title }}</h1>
          <p class="mt-1"><app-icon name="pin" [size]="16" /> {{ d.event.venue }}, {{ d.event.placeName }}@if (d.event.state) {, {{ d.event.state }} }</p>
          @if (d.event.status === 'CANCELLED') { <p class="mt-3 inline-block rounded-full bg-white/20 px-3 py-1 text-sm font-bold">Cancelled</p> }
        </header>
        @if (d.description) { <p class="mt-5 whitespace-pre-line text-slate-700 dark:text-slate-300">{{ d.description }}</p> }

        <section class="mt-6" aria-labelledby="event-rides-heading">
          <div class="mb-2 flex flex-wrap items-center justify-between gap-2">
            <h2 id="event-rides-heading" class="kicker">{{ d.rides.length }} {{ d.rides.length === 1 ? 'ride' : 'rides' }} going there</h2>
            @if (features.isOn('rides.publish') && d.event.status === 'LIVE') {
              <a routerLink="/publish" [queryParams]="{ toId: d.event.placeId }" class="btn btn-primary btn-sm"><app-icon name="plusCircle" [size]="16" /> Offer a ride there</a>
            }
          </div>
          @if (d.rides.length) {
            <ul class="space-y-2">
              @for (r of d.rides; track r.id) {
                <li>
                  <a [routerLink]="['/rides', r.id]" class="card flex items-center gap-3 p-4 transition hover:shadow-md">
                    <app-avatar [url]="r.driver.avatarUrl" [name]="r.driver.displayName" [size]="40" />
                    <span class="min-w-0 flex-1"><span class="block truncate font-semibold">{{ r.fromName }} → {{ r.toName }}</span>
                      <span class="block text-sm text-slate-500 dark:text-slate-400">{{ r.departureAt | date: 'EEE d MMM, h:mm a' }} · {{ r.driver.displayName }}</span></span>
                    @if (r.ladiesOnly) { <span class="badge badge-muted">Women only</span> }
                    <app-icon name="arrowRight" [size]="18" class="text-slate-400" />
                  </a>
                </li>
              }
            </ul>
          } @else { <p class="card p-6 text-center text-sm text-slate-600 dark:text-slate-400">No rides there yet. Going by car? Offer the empty seats and split the cost.</p> }
        </section>
      } @else {
        <div class="skeleton mt-4 h-40"></div>
      }
    </div>
  `,
})
export class EventPage {
  private readonly api = inject(SocialApi);
  protected readonly features = inject(FeaturesService);

  readonly id = input.required<string>();
  protected readonly data = signal<{ event: EventCard; description: string | null; rides: RideCard[] } | null>(null);
  protected readonly error = signal<string | null>(null);

  constructor() {
    effect(() => {
      const id = this.id();
      untracked(async () => {
        try {
          this.data.set(await this.api.event(id));
        } catch (e) {
          this.error.set(problemOf(e).detail);
        }
      });
    });
  }
}
