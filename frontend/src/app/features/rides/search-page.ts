import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, effect, inject, input, numberAttribute, signal, untracked } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { AuthService } from '../../core/auth.service';
import { problemOf } from '../../core/errors';
import { FeaturesService } from '../../core/features.service';
import { addDays, dayOf, duration, rupees } from '../../core/rides-format';
import { SearchResponse, SearchResult } from '../../core/rides.models';
import { RidesApi } from '../../core/rides-api';
import { SocialApi } from '../../core/social-api';
import { PromoCard, PromotionsApi } from '../../core/promotions-api';
import { SponsoredCard } from '../../shared/sponsored-card';
import { todayIso } from '../../core/util';
import { Avatar } from '../../shared/avatar';
import { Icon } from '../../shared/icon';
import { RideSearchForm } from '../../shared/ride-search-form';
import { Stars } from '../../shared/stars';

type Sort = 'time' | 'price' | 'near';

const optionalNumber = (v: unknown) => (v == null || v === '' ? undefined : numberAttribute(v));

/** Ride search results for /search?from=…&fromId=…&to=…&toId=…&date=…&seats=… */
@Component({
  selector: 'app-search-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [DatePipe, RouterLink, Icon, Avatar, RideSearchForm, Stars, SponsoredCard],
  template: `
    <div class="container-page py-6 sm:py-10">
      <h1 class="sr-only">Find a ride</h1>
      <app-ride-search-form [from]="result()?.from ?? null" [to]="result()?.to ?? null" [date]="result()?.date ?? date()" [seats]="seats()" />

      @if (!hasQuery()) {
        <section class="card mx-auto mt-8 max-w-2xl p-8 text-center motion-safe:animate-fade-up sm:p-12">
          <span class="mx-auto flex h-16 w-16 items-center justify-center rounded-3xl bg-brand-50 text-brand-600 dark:bg-brand-950 dark:text-brand-400">
            <app-icon name="route" [size]="30" />
          </span>
          <h2 class="mt-5 text-2xl font-extrabold tracking-tight">Where are you headed?</h2>
          <p class="mx-auto mt-3 max-w-md text-slate-600 dark:text-slate-400">Pick where you're leaving from and where you're going. We'll show every driver heading your way.</p>
        </section>
      } @else if (error()) {
        <section class="card mx-auto mt-8 max-w-2xl p-8 text-center" role="alert">
          <app-icon name="info" [size]="28" class="text-accent-500" />
          <p class="mt-3 font-semibold">{{ error() }}</p>
          <button type="button" class="btn btn-secondary mt-5" (click)="reload()"><app-icon name="refresh" [size]="18" /> Try again</button>
        </section>
      } @else {
        <div class="mt-6 grid gap-6 lg:grid-cols-[16rem_1fr]">
          <!-- Sort and filters -->
          <aside class="space-y-4 lg:sticky lg:top-24 lg:self-start" aria-label="Sort and filter">
            <div class="card p-4">
              <p class="kicker mb-2">Sort by</p>
              <div class="segmented grid-cols-3 lg:grid-cols-1" role="radiogroup" aria-label="Sort by">
                @for (s of sorts; track s.key) {
                  <button type="button" role="radio" class="segment lg:justify-start" [class.segment-on]="sort() === s.key"
                          [attr.aria-checked]="sort() === s.key" (click)="sort.set(s.key)">
                    <app-icon [name]="s.icon" [size]="16" /> {{ s.label }}
                  </button>
                }
              </div>
              <p class="kicker mt-5 mb-2">Show only</p>
              <div class="flex flex-wrap gap-2">
                <button type="button" class="chip" [class.chip-on]="instantOnly()" [attr.aria-pressed]="instantOnly()" (click)="instantOnly.set(!instantOnly())">
                  <app-icon name="sparkles" [size]="16" /> Instant booking
                </button>
                @if (isWoman()) {
                  <button type="button" class="chip" [class.chip-on]="ladiesOnly()" [attr.aria-pressed]="ladiesOnly()" (click)="ladiesOnly.set(!ladiesOnly())">
                    <app-icon name="woman" [size]="16" /> Ladies only
                  </button>
                }
              </div>
            </div>
          </aside>

          <section aria-live="polite" [attr.aria-busy]="loading()">
            @if (result(); as r) {
              <div class="mb-4 flex flex-wrap items-end justify-between gap-3">
                <div>
                  <h2 class="text-xl font-extrabold tracking-tight sm:text-2xl">{{ r.from.name }} <span class="text-slate-400">→</span> {{ r.to.name }}</h2>
                  <p class="mt-0.5 text-sm text-slate-500 dark:text-slate-400">
                    {{ dayOf(r.date) | date: 'EEEE, d MMMM' : 'UTC' }} · {{ r.seats }} {{ r.seats === 1 ? 'passenger' : 'passengers' }} ·
                    <b class="text-slate-700 dark:text-slate-300">{{ rides().length }} {{ rides().length === 1 ? 'ride' : 'rides' }}</b>
                  </p>
                </div>
                <div class="flex gap-2">
                  <button type="button" class="btn btn-secondary btn-sm" (click)="shiftDay(-1)" [disabled]="r.date <= today" aria-label="Previous day">
                    <app-icon name="arrowLeft" [size]="16" /> <span class="max-sm:sr-only">Previous day</span>
                  </button>
                  <button type="button" class="btn btn-secondary btn-sm" (click)="shiftDay(1)" aria-label="Next day">
                    <span class="max-sm:sr-only">Next day</span> <app-icon name="arrowRight" [size]="16" />
                  </button>
                </div>
              </div>
            }

            @if (loading() && !result()) {
              @for (i of [1, 2, 3]; track i) {
                <div class="card mb-3 p-5"><div class="skeleton h-5 w-1/3"></div><div class="skeleton mt-4 h-4 w-2/3"></div><div class="skeleton mt-3 h-4 w-1/2"></div></div>
              }
            } @else if (result(); as r) {
              <ul class="space-y-3" [class.opacity-60]="loading()">
                @for (ride of rides(); track ride.rideId) {
                  <li>
                    <a [routerLink]="['/rides', ride.rideId]" [queryParams]="{ from: ride.fromPosition, to: ride.toPosition, seats: r.seats }"
                       class="card group block p-4 transition hover:-translate-y-0.5 hover:shadow-lg hover:shadow-slate-900/5 focus-visible:ring-4 focus-visible:ring-brand-500/30 focus-visible:outline-none sm:p-5">
                      <div class="flex gap-4">
                        <div class="w-[4.75rem] shrink-0 text-right whitespace-nowrap">
                          <p class="font-bold">{{ ride.pickup.at | date: 'h:mm a' }}</p>
                          <p class="my-3 text-xs text-slate-500 dark:text-slate-400">{{ fmtDuration(minutesBetween(ride.pickup.at, ride.drop.at)) }}</p>
                          <p class="font-bold">{{ ride.drop.at | date: 'h:mm a' }}</p>
                        </div>
                        <div class="min-w-0 flex-1">
                          <div class="relative flex gap-3 pb-5">
                            <span class="route-dot"></span><span class="route-line"></span>
                            <div class="min-w-0">
                              <p class="truncate font-bold">{{ ride.pickup.name }}</p>
                              @if (ride.pickupDistanceKm >= 2) {
                                <p class="text-xs text-accent-600 dark:text-accent-300">{{ ride.pickupDistanceKm }} km from {{ r.from.name }}</p>
                              }
                            </div>
                          </div>
                          <div class="relative flex gap-3">
                            <span class="route-dot"></span>
                            <div class="min-w-0">
                              <p class="truncate font-bold">{{ ride.drop.name }}</p>
                              @if (ride.dropDistanceKm >= 2) {
                                <p class="text-xs text-accent-600 dark:text-accent-300">{{ ride.dropDistanceKm }} km from {{ r.to.name }}</p>
                              }
                            </div>
                          </div>
                        </div>
                        <div class="shrink-0 text-right">
                          <p class="text-xl font-extrabold tracking-tight">{{ fmtRupees(ride.price) }}</p>
                          <p class="text-xs text-slate-500 dark:text-slate-400">per seat</p>
                        </div>
                      </div>
                      <div class="mt-4 flex flex-wrap items-center gap-x-3 gap-y-2 border-t border-slate-100 pt-3 dark:border-slate-800">
                        <span class="flex min-w-0 items-center gap-2">
                          <app-avatar [url]="ride.driver.avatarUrl" [name]="ride.driver.displayName" [size]="32" />
                          <span class="truncate text-sm font-semibold">{{ ride.driver.displayName }}</span>
                          @if (ride.driver.idVerified) { <app-icon name="shield" [size]="15" class="text-brand-600" /> }
                          <app-stars class="text-sm" [value]="ride.driver.rating" [count]="ride.driver.reviewCount" />
                          @if (vibe()[ride.driver.id ?? '']; as v) {
                            <span class="badge" [class]="v >= 70 ? 'tone-success' : 'tone-muted'" [attr.title]="'Vibe match from travel preferences and interests'">✨ {{ v }}% vibe</span>
                          }
                        </span>
                        @if (ride.car) { <span class="text-sm text-slate-500 dark:text-slate-400">{{ ride.car }}</span> }
                        <span class="ml-auto flex flex-wrap items-center gap-1.5">
                          @if (ride.yours) {
                            <span class="badge tone-muted">Your ride</span>
                          } @else if (ride.yourBooking) {
                            <span class="badge tone-success"><app-icon name="check" [size]="13" /> {{ ride.yourBooking === 'CONFIRMED' ? 'Booked' : 'Requested' }}</span>
                          }
                          @if (ride.ladiesOnly) { <span class="badge bg-pink-50 text-pink-700 dark:bg-pink-500/15 dark:text-pink-300"><app-icon name="woman" [size]="13" /> Ladies only</span> }
                          @if (ride.instantBooking) { <span class="badge tone-success"><app-icon name="sparkles" [size]="13" /> Instant</span> }
                          <span class="badge tone-muted"><app-icon name="users" [size]="13" /> {{ ride.seatsLeft }} {{ ride.seatsLeft === 1 ? 'seat' : 'seats' }} left</span>
                        </span>
                      </div>
                    </a>
                  </li>
                  @if ($index === 1 || ($last && $index < 1)) {
                    @for (p of sponsored(); track p.id) { <li><app-sponsored-card [card]="p" /></li> }
                  }
                } @empty {
                  <li class="card p-8 text-center sm:p-12">
                    <span class="mx-auto flex h-14 w-14 items-center justify-center rounded-3xl bg-slate-100 text-slate-500 dark:bg-slate-800 dark:text-slate-400">
                      <app-icon name="calendar" [size]="26" />
                    </span>
                    @if (r.rides.length) {
                      <h3 class="mt-4 text-lg font-bold">No rides match these filters</h3>
                      <button type="button" class="btn btn-secondary mt-4" (click)="instantOnly.set(false); ladiesOnly.set(false)">Show all {{ r.rides.length }} rides</button>
                    } @else {
                      <h3 class="mt-4 text-lg font-bold">No rides on {{ dayOf(r.date) | date: 'EEE, d MMM' : 'UTC' }} yet</h3>
                      <p class="mx-auto mt-2 max-w-md text-sm text-slate-600 dark:text-slate-400">
                        Drivers often add rides a day or two before they travel, so check again soon.
                      </p>
                      @if (r.otherDates.length) {
                        <p class="kicker mt-6 mb-2">Rides on other days</p>
                        <div class="flex flex-wrap justify-center gap-2">
                          @for (d of r.otherDates; track d.date) {
                            <button type="button" class="chip" (click)="goToDate(d.date)">
                              {{ dayOf(d.date) | date: 'EEE, d MMM' : 'UTC' }} · <b>{{ d.rides }}</b> {{ d.rides === 1 ? 'ride' : 'rides' }}
                            </button>
                          }
                        </div>
                      }
                    }
                    @if (features.isOn('rides.publish')) {
                      <a routerLink="/publish" [queryParams]="{ fromId: r.from.id, toId: r.to.id }" class="btn btn-primary mt-6">
                        <app-icon name="car" [size]="18" /> Driving there? Offer this ride
                      </a>
                    }
                  </li>
                }
              </ul>
            }
          </section>
        </div>
      }
    </div>
  `,
})
export class SearchPage {
  private readonly api = inject(RidesApi);
  private readonly router = inject(Router);
  private readonly auth = inject(AuthService);
  protected readonly features = inject(FeaturesService);

  readonly from = input<string>();
  readonly fromId = input<number | undefined, unknown>(undefined, { transform: optionalNumber });
  readonly to = input<string>();
  readonly toId = input<number | undefined, unknown>(undefined, { transform: optionalNumber });
  readonly date = input<string>();
  readonly seats = input<number | undefined, unknown>(undefined, { transform: optionalNumber });

  protected readonly today = todayIso();
  protected readonly sorts: ReadonlyArray<{ key: Sort; label: string; icon: 'clock' | 'rupee' | 'pin' }> = [
    { key: 'time', label: 'Earliest', icon: 'clock' },
    { key: 'price', label: 'Cheapest', icon: 'rupee' },
    { key: 'near', label: 'Closest', icon: 'pin' },
  ];
  protected readonly result = signal<SearchResponse | null>(null);
  protected readonly vibe = signal<Record<string, number>>({});
  protected readonly sponsored = signal<PromoCard[]>([]);
  private readonly social = inject(SocialApi);
  private readonly promotions = inject(PromotionsApi);
  protected readonly loading = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly sort = signal<Sort>('time');
  protected readonly instantOnly = signal(false);
  protected readonly ladiesOnly = signal(false);

  protected readonly hasQuery = computed(() => !!(this.fromId() || this.from()) && !!(this.toId() || this.to()));
  protected readonly isWoman = computed(() => this.auth.user()?.gender === 'FEMALE');
  protected readonly rides = computed(() => {
    const r = this.result();
    if (!r) return [];
    const list = r.rides.filter((x) => (!this.instantOnly() || x.instantBooking) && (!this.ladiesOnly() || x.ladiesOnly));
    const by: Record<Sort, (a: SearchResult, b: SearchResult) => number> = {
      time: (a, b) => a.pickup.at.localeCompare(b.pickup.at),
      price: (a, b) => a.price - b.price || a.pickup.at.localeCompare(b.pickup.at),
      near: (a, b) => a.pickupDistanceKm + a.dropDistanceKm - (b.pickupDistanceKm + b.dropDistanceKm) || a.pickup.at.localeCompare(b.pickup.at),
    };
    return [...list].sort(by[this.sort()]);
  });

  protected readonly fmtRupees = rupees;
  protected readonly fmtDuration = duration;
  protected readonly dayOf = dayOf;

  private requestNo = 0;

  constructor() {
    effect(() => {
      const query = {
        fromId: this.fromId(),
        from: this.fromId() ? undefined : this.from(),
        toId: this.toId(),
        to: this.toId() ? undefined : this.to(),
        date: this.date(),
        seats: this.seats(),
      };
      this.auth.user()?.id;
      untracked(() => {
        if (this.hasQuery()) void this.load(query);
      });
    });
  }

  protected reload(): void {
    void this.load({ fromId: this.fromId(), from: this.from(), toId: this.toId(), to: this.to(), date: this.date(), seats: this.seats() });
  }

  protected shiftDay(days: number): void {
    const current = this.result()?.date ?? this.date() ?? this.today;
    const next = addDays(current, days);
    if (next >= this.today) this.goToDate(next);
  }

  protected goToDate(date: string): void {
    void this.router.navigate([], { queryParams: { date }, queryParamsHandling: 'merge' });
  }

  protected minutesBetween(a: string, b: string): number {
    return Math.round((Date.parse(b) - Date.parse(a)) / 60_000);
  }

  /** One sponsored card for these towns, when promotions in search are on (members only). */
  private async loadSponsored(placeIds: number[]): Promise<void> {
    this.sponsored.set([]);
    if (!this.features.isOn('promotions.in_search') || !this.auth.isLoggedIn()) return;
    try {
      this.sponsored.set(await this.promotions.feed('SEARCH', placeIds, 1));
    } catch {
      // Search works without it.
    }
  }

  /** Vibe scores with the drivers in the results, when vibe match is on. */
  private async loadVibe(driverIds: string[]): Promise<void> {
    if (!this.features.isOn('vibe_match') || !this.auth.isLoggedIn()) return;
    try {
      this.vibe.set(await this.social.vibeScores([...new Set(driverIds)]));
    } catch {
      this.vibe.set({});
    }
  }

  private async load(query: Parameters<RidesApi['search']>[0]): Promise<void> {
    const no = ++this.requestNo;
    this.loading.set(true);
    this.error.set(null);
    try {
      const r = await this.api.search(query);
      if (no === this.requestNo) {
        this.result.set(r);
        void this.loadVibe(r.rides.map((x) => x.driver.id).filter((id): id is string => !!id));
        void this.loadSponsored([r.from.id, r.to.id]);
      }
    } catch (e) {
      if (no === this.requestNo) {
        this.result.set(null);
        this.error.set(problemOf(e).detail);
      }
    } finally {
      if (no === this.requestNo) this.loading.set(false);
    }
  }
}
