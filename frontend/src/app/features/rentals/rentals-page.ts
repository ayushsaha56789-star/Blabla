import { ChangeDetectionStrategy, Component, inject, input, OnInit, signal } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { AuthService } from '../../core/auth.service';
import { problemOf } from '../../core/errors';
import { FeaturesService } from '../../core/features.service';
import { fromLocalInput, ListingCard, RentalMode, RentalsApi, toLocalInput } from '../../core/rentals-api';
import { rupees } from '../../core/rides-format';
import { RidesApi } from '../../core/rides-api';
import { Place } from '../../core/rides.models';
import { Icon } from '../../shared/icon';
import { PlaceInput } from '../../shared/place-input';
import { Stars } from '../../shared/stars';

/** Rent a car: search by town, dates and type. */
@Component({
  selector: 'app-rentals-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, Icon, PlaceInput, Stars],
  template: `
    <section class="relative overflow-hidden bg-linear-to-br from-sky-50 via-white to-indigo-50 dark:from-slate-950 dark:via-slate-950 dark:to-indigo-950/40">
      <div class="container-page py-10 sm:py-14">
        <p class="kicker">Rent a car</p>
        <h1 class="mt-1 max-w-2xl text-3xl font-extrabold tracking-tight sm:text-5xl">Self-drive, or with a driver.<br class="hidden sm:block" /> From people near you.</h1>
        <p class="mt-3 max-w-xl text-slate-600 dark:text-slate-400">Clear prices, kilometres and deposits up front. Photos at pickup and return, so nobody argues later.</p>

        <form class="card mt-6 grid gap-3 p-4 sm:p-5 lg:grid-cols-[1.4fr_1fr_1fr_auto] lg:items-end" (submit)="find($event)">
          <div>
            <label class="label text-xs" for="rent-place">Where</label>
            <app-place-input inputId="rent-place" [(value)]="place" placeholder="Town or city" />
          </div>
          <div>
            <label class="label text-xs" for="rent-from">Pickup</label>
            <input id="rent-from" type="datetime-local" class="input" [min]="minFrom" [value]="from()" (input)="setFrom($any($event.target).value)" />
          </div>
          <div>
            <label class="label text-xs" for="rent-to">Return</label>
            <input id="rent-to" type="datetime-local" class="input" [min]="from()" [value]="to()" (input)="to.set($any($event.target).value)" />
          </div>
          <button type="submit" class="btn btn-primary h-12" [disabled]="busy()"><app-icon name="search" [size]="18" /> {{ busy() ? 'Searching…' : 'Find cars' }}</button>
          <div class="segmented grid-cols-3 lg:col-span-4" role="radiogroup" aria-label="Type">
            @for (m of modes; track m.label) {
              <button type="button" role="radio" class="segment" [class.segment-on]="mode() === m.value" [attr.aria-checked]="mode() === m.value" (click)="mode.set(m.value)">{{ m.label }}</button>
            }
          </div>
        </form>
        @if (auth.isLoggedIn()) {
          <p class="mt-4 text-sm text-slate-600 dark:text-slate-400">Own a car? <a routerLink="/rentals/new" class="font-semibold text-brand-700 underline dark:text-brand-400">List it for rent</a> and earn when you don't need it. · <a routerLink="/rentals/mine" class="font-semibold text-brand-700 underline dark:text-brand-400">My rentals</a></p>
        }
      </div>
    </section>

    <div class="container-page py-8">
      @if (error()) {
        <p class="card p-6 text-center" role="alert">{{ error() }}</p>
      } @else if (results(); as list) {
        @if (!list.length) {
          <div class="card p-10 text-center">
            <app-icon name="car" [size]="32" class="text-slate-400" />
            <p class="mt-3 font-semibold">No cars free near {{ place()?.name }} for those dates.</p>
            <p class="mt-1 text-sm text-slate-600 dark:text-slate-400">Try other dates or a bigger town nearby.</p>
          </div>
        } @else {
          <p class="kicker mb-3">{{ list.length }} {{ list.length === 1 ? 'car' : 'cars' }} near {{ place()?.name }}</p>
          <ul class="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
            @for (c of list; track c.id) {
              <li>
                <a [routerLink]="['/rentals', c.id]" [queryParams]="linkParams()" class="card block overflow-hidden transition hover:shadow-lg">
                  <div class="aspect-[16/10] bg-slate-100 dark:bg-slate-800">
                    @if (c.photoUrl) { <img [src]="c.photoUrl" [alt]="c.car.name" class="h-full w-full object-cover" loading="lazy" /> }
                    @else { <div class="flex h-full items-center justify-center text-slate-400"><app-icon name="car" [size]="40" /></div> }
                  </div>
                  <div class="p-4">
                    <div class="flex items-start justify-between gap-2">
                      <div class="min-w-0">
                        <p class="truncate font-bold">{{ c.car.name }}</p>
                        <p class="truncate text-sm text-slate-500 dark:text-slate-400">{{ c.area }} · {{ c.placeName }}@if (c.distanceKm !== null) { · {{ c.distanceKm }} km }</p>
                      </div>
                      @if (c.verified) { <span class="badge badge-success shrink-0"><app-icon name="shield" [size]="12" /> Verified</span> }
                    </div>
                    <div class="mt-3 flex flex-wrap gap-1.5">
                      @if (c.selfDrive) { <span class="chip">Self-drive</span> }
                      @if (c.withDriver) { <span class="chip">With driver</span> }
                      @if (c.instant) { <span class="chip"><app-icon name="sparkles" [size]="12" /> Instant</span> }
                    </div>
                    <div class="mt-3 flex items-end justify-between gap-2">
                      <span class="flex items-center gap-1 text-sm text-slate-600 dark:text-slate-400">{{ c.owner.displayName }} <app-stars [value]="c.owner.rating" [count]="c.owner.reviewCount" /></span>
                      <span class="text-right">
                        @if (c.priceForPeriod !== null) {
                          <span class="block text-lg font-extrabold">{{ money(c.priceForPeriod) }}</span>
                          <span class="block text-xs text-slate-500 dark:text-slate-400">for your dates</span>
                        } @else {
                          <span class="block text-lg font-extrabold">{{ money(c.dayPrice) }}<span class="text-sm font-medium text-slate-500">/day</span></span>
                        }
                      </span>
                    </div>
                  </div>
                </a>
              </li>
            }
          </ul>
        }
      } @else if (!busy()) {
        <div class="grid gap-4 text-center sm:grid-cols-3">
          @for (p of promises; track p.title) {
            <div class="card p-6">
              <span class="mx-auto flex h-12 w-12 items-center justify-center rounded-2xl bg-brand-50 text-brand-700 dark:bg-brand-500/15 dark:text-brand-300"><app-icon [name]="p.icon" [size]="24" /></span>
              <p class="mt-3 font-bold">{{ p.title }}</p>
              <p class="mt-1 text-sm text-slate-600 dark:text-slate-400">{{ p.text }}</p>
            </div>
          }
        </div>
      }
    </div>
  `,
})
export class RentalsPage implements OnInit {
  private readonly api = inject(RentalsApi);
  private readonly rides = inject(RidesApi);
  private readonly router = inject(Router);
  protected readonly auth = inject(AuthService);
  protected readonly features = inject(FeaturesService);

  readonly placeId = input<string | undefined>(undefined);
  readonly fromParam = input<string | undefined>(undefined, { alias: 'from' });
  readonly toParam = input<string | undefined>(undefined, { alias: 'to' });
  readonly modeParam = input<string | undefined>(undefined, { alias: 'mode' });

  protected readonly modes: { label: string; value: RentalMode | null }[] = [
    { label: 'Any', value: null }, { label: 'Self-drive', value: 'SELF_DRIVE' }, { label: 'With driver', value: 'WITH_DRIVER' },
  ];
  protected readonly promises = [
    { icon: 'shield' as const, title: 'Verified owners', text: 'Staff check every car’s registration and insurance.' },
    { icon: 'camera' as const, title: 'Photo check', text: 'Both sides record the car at pickup and return.' },
    { icon: 'rupee' as const, title: 'Deposit protected', text: 'It comes back after the return, minus only agreed charges.' },
  ];
  protected readonly minFrom = toLocalInput(new Date(Date.now() + 3600_000));
  protected readonly place = signal<Place | null>(null);
  protected readonly from = signal(toLocalInput(nextHour(3)));
  protected readonly to = signal(toLocalInput(new Date(nextHour(3).getTime() + 24 * 3600_000)));
  protected readonly mode = signal<RentalMode | null>(null);
  protected readonly results = signal<ListingCard[] | null>(null);
  protected readonly busy = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly money = rupees;

  async ngOnInit(): Promise<void> {
    if (this.fromParam()) this.from.set(this.fromParam()!);
    if (this.toParam()) this.to.set(this.toParam()!);
    const m = this.modeParam();
    if (m === 'SELF_DRIVE' || m === 'WITH_DRIVER') this.mode.set(m);
    const id = Number(this.placeId());
    if (id) {
      try {
        this.place.set(await this.rides.place(id));
        await this.search();
      } catch {
        // The form still works.
      }
    }
  }

  protected setFrom(value: string): void {
    this.from.set(value);
    const f = new Date(value);
    if (!Number.isNaN(f.getTime()) && new Date(this.to()).getTime() <= f.getTime()) {
      this.to.set(toLocalInput(new Date(f.getTime() + 24 * 3600_000)));
    }
  }

  protected linkParams(): Record<string, string> {
    return { from: this.from(), to: this.to(), ...(this.mode() ? { mode: this.mode()! } : {}) };
  }

  protected async find(event: Event): Promise<void> {
    event.preventDefault();
    const p = this.place();
    if (!p) {
      this.error.set('Choose a town first.');
      return;
    }
    void this.router.navigate([], { queryParams: { placeId: p.id, ...this.linkParams() }, replaceUrl: true });
    await this.search();
  }

  private async search(): Promise<void> {
    const p = this.place();
    if (!p) return;
    this.busy.set(true);
    this.error.set(null);
    try {
      this.results.set(await this.api.search(p.id, fromLocalInput(this.from()), fromLocalInput(this.to()), this.mode()));
    } catch (e) {
      this.error.set(problemOf(e).detail);
    } finally {
      this.busy.set(false);
    }
  }
}

/** A round hour some hours from now. */
function nextHour(hours: number): Date {
  const d = new Date(Date.now() + hours * 3600_000);
  d.setMinutes(0, 0, 0);
  return d;
}
