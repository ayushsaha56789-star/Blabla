import { ChangeDetectionStrategy, Component, computed, inject, input, OnInit, signal } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { AuthService } from '../../core/auth.service';
import { DriverCard, DriversApi, DriverSort, TripKind, tripLength } from '../../core/drivers-api';
import { problemOf } from '../../core/errors';
import { FeaturesService } from '../../core/features.service';
import { fromLocalInput, toLocalInput } from '../../core/rentals-api';
import { rupees } from '../../core/rides-format';
import { RidesApi } from '../../core/rides-api';
import { Place } from '../../core/rides.models';
import { Avatar } from '../../shared/avatar';
import { Icon } from '../../shared/icon';
import { PlaceInput } from '../../shared/place-input';
import { Stars } from '../../shared/stars';

/** Hire a driver for your own car: where, when, how long, and which kind of car. */
@Component({
  selector: 'app-drivers-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, Avatar, Icon, PlaceInput, Stars],
  template: `
    <section class="relative overflow-hidden bg-linear-to-br from-teal-50 via-white to-emerald-50 dark:from-slate-950 dark:via-slate-950 dark:to-emerald-950/40">
      <div class="container-page py-10 sm:py-14">
        <p class="kicker">Hire a driver</p>
        <h1 class="mt-1 max-w-2xl text-3xl font-extrabold tracking-tight sm:text-5xl">Your car. A checked driver.<br class="hidden sm:block" /> Sit back.</h1>
        <p class="mt-3 max-w-xl text-slate-600 dark:text-slate-400">For a day of errands, a wedding, an airport run or a trip out of town. Every driver's licence is checked, and the price is clear before you book.</p>

        @if (features.isOn('drivers.hire')) {
          <form class="card mt-6 grid gap-3 p-4 sm:p-5 lg:grid-cols-[1.3fr_1fr_0.8fr_auto] lg:items-end" (submit)="find($event)">
            <div>
              <label class="label text-xs" for="drv-place">Pickup town</label>
              <app-place-input inputId="drv-place" [(value)]="place" placeholder="Town or city" />
            </div>
            <div>
              <label class="label text-xs" for="drv-from">When</label>
              <input id="drv-from" type="datetime-local" class="input" [min]="minFrom" [value]="from()" (input)="from.set($any($event.target).value)" />
            </div>
            <div>
              @if (kind() === 'LOCAL') {
                <label class="label text-xs" for="drv-hours">For how long</label>
                <select id="drv-hours" class="input" [value]="hours()" (change)="hours.set(+$any($event.target).value)">
                  @for (h of hourChoices; track h) { <option [value]="h" [selected]="h === hours()">{{ h }} hours</option> }
                </select>
              } @else {
                <label class="label text-xs" for="drv-days">For how many days</label>
                <select id="drv-days" class="input" [value]="days()" (change)="days.set(+$any($event.target).value)">
                  @for (d of dayChoices; track d) { <option [value]="d" [selected]="d === days()">{{ d }} {{ d === 1 ? 'day' : 'days' }}</option> }
                </select>
              }
            </div>
            <button type="submit" class="btn btn-primary h-12" [disabled]="busy()"><app-icon name="search" [size]="18" /> {{ busy() ? 'Searching…' : 'Find drivers' }}</button>
            <div class="grid gap-2 sm:grid-cols-2 lg:col-span-4">
              <div class="segmented grid-cols-2" role="radiogroup" aria-label="Trip">
                <button type="button" role="radio" class="segment" [class.segment-on]="kind() === 'LOCAL'" [attr.aria-checked]="kind() === 'LOCAL'" (click)="kind.set('LOCAL')">In town</button>
                <button type="button" role="radio" class="segment" [class.segment-on]="kind() === 'OUTSTATION'" [attr.aria-checked]="kind() === 'OUTSTATION'" (click)="kind.set('OUTSTATION')">Outstation</button>
              </div>
              <div class="segmented grid-cols-2" role="radiogroup" aria-label="Your car">
                <button type="button" role="radio" class="segment" [class.segment-on]="!automatic()" [attr.aria-checked]="!automatic()" (click)="automatic.set(false)">Manual car</button>
                <button type="button" role="radio" class="segment" [class.segment-on]="automatic()" [attr.aria-checked]="automatic()" (click)="automatic.set(true)">Automatic car</button>
              </div>
            </div>
            @if (isWoman()) {
              <label class="flex items-center gap-2 text-sm lg:col-span-4"><input type="checkbox" class="h-4 w-4 accent-brand-600" [checked]="womenOnly()" (change)="womenOnly.set($any($event.target).checked)" /> Women drivers only</label>
            }
          </form>
        }
        @if (auth.isLoggedIn()) {
          <p class="mt-4 text-sm text-slate-600 dark:text-slate-400">
            @if (features.isOn('drivers.register')) { Drive well and know your town? <a routerLink="/drivers/join" class="font-semibold text-brand-700 underline dark:text-brand-400">Become a driver partner</a> · }
            <a routerLink="/drivers/trips" class="font-semibold text-brand-700 underline dark:text-brand-400">My driver trips</a>
          </p>
        }
      </div>
    </section>

    <div class="container-page py-8">
      @if (error()) {
        <p class="card p-6 text-center" role="alert">{{ error() }}</p>
      } @else if (sorted(); as list) {
        @if (!list.length) {
          <div class="card p-10 text-center">
            <app-icon name="steering" [size]="32" class="text-slate-400" />
            <p class="mt-3 font-semibold">No drivers free near {{ place()?.name }} then.</p>
            <p class="mt-1 text-sm text-slate-600 dark:text-slate-400">Try another time, a shorter trip, or a bigger town nearby.</p>
          </div>
        } @else {
          <div class="mb-3 flex flex-wrap items-center justify-between gap-2">
            <p class="kicker">{{ list.length }} {{ list.length === 1 ? 'driver' : 'drivers' }} near {{ place()?.name }} · {{ length() }}</p>
            <div class="segmented grid-cols-3 text-sm" role="radiogroup" aria-label="Sort">
              @for (s of sorts; track s.value) {
                <button type="button" role="radio" class="segment px-3" [class.segment-on]="sort() === s.value" [attr.aria-checked]="sort() === s.value" (click)="sort.set(s.value)">{{ s.label }}</button>
              }
            </div>
          </div>
          <ul class="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
            @for (d of list; track d.id) {
              <li>
                <a [routerLink]="['/drivers', d.id]" [queryParams]="linkParams()" class="card block p-4 transition hover:shadow-lg">
                  <div class="flex items-start gap-3">
                    <app-avatar [url]="d.member.avatarUrl" [name]="d.member.displayName" [size]="56" />
                    <div class="min-w-0 flex-1">
                      <p class="truncate font-bold">{{ d.member.displayName }}</p>
                      <p class="flex items-center gap-1 text-sm text-slate-600 dark:text-slate-400"><app-stars [value]="d.rating" [count]="d.reviewCount" /></p>
                      <p class="text-sm text-slate-500 dark:text-slate-400">{{ d.experienceYears }} {{ d.experienceYears === 1 ? 'year' : 'years' }} driving · {{ d.tripsDone }} {{ d.tripsDone === 1 ? 'trip' : 'trips' }}</p>
                    </div>
                    <span class="text-right">
                      <span class="block text-lg font-extrabold">{{ money(d.fare ?? 0) }}</span>
                      <span class="block text-xs text-slate-500 dark:text-slate-400">for {{ length() }}</span>
                    </span>
                  </div>
                  <div class="mt-3 flex flex-wrap gap-1.5">
                    <span class="badge badge-success"><app-icon name="idCard" [size]="12" /> Licence checked</span>
                    @if (d.policeChecked) { <span class="badge badge-success"><app-icon name="shield" [size]="12" /> Police verified</span> }
                    @for (l of d.languages; track l) { <span class="chip">{{ l }}</span> }
                  </div>
                  <p class="mt-3 truncate text-xs text-slate-500 dark:text-slate-400">Works in {{ d.areas.join(', ') }}@if (d.distanceKm !== null) { · {{ d.distanceKm }} km away }</p>
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
export class DriversPage implements OnInit {
  private readonly api = inject(DriversApi);
  private readonly rides = inject(RidesApi);
  private readonly router = inject(Router);
  protected readonly auth = inject(AuthService);
  protected readonly features = inject(FeaturesService);

  readonly placeId = input<string | undefined>(undefined);
  readonly fromParam = input<string | undefined>(undefined, { alias: 'from' });
  readonly kindParam = input<string | undefined>(undefined, { alias: 'kind' });
  readonly hoursParam = input<string | undefined>(undefined, { alias: 'hours' });
  readonly daysParam = input<string | undefined>(undefined, { alias: 'days' });
  readonly automaticParam = input<string | undefined>(undefined, { alias: 'automatic' });

  protected readonly hourChoices = [2, 3, 4, 5, 6, 8, 10, 12];
  protected readonly dayChoices = Array.from({ length: 15 }, (_, i) => i + 1);
  protected readonly sorts: { label: string; value: DriverSort }[] = [
    { label: 'Best', value: 'BEST' }, { label: 'Cheapest', value: 'PRICE' }, { label: 'Nearest', value: 'NEAREST' },
  ];
  protected readonly promises = [
    { icon: 'idCard' as const, title: 'Checked licences', text: 'Our team checks every driver’s licence before they can be booked.' },
    { icon: 'key' as const, title: 'A code to start', text: 'You give the driver a 4-digit code when they arrive, so you know it’s them.' },
    { icon: 'shieldAlert' as const, title: 'SOS on every trip', text: 'One tap alerts SaathChalo and your emergency contacts.' },
  ];
  protected readonly minFrom = toLocalInput(new Date(Date.now() + 3600_000));
  protected readonly place = signal<Place | null>(null);
  protected readonly from = signal(toLocalInput(nextHour(3)));
  protected readonly kind = signal<TripKind>('LOCAL');
  protected readonly hours = signal(4);
  protected readonly days = signal(1);
  protected readonly automatic = signal(false);
  protected readonly womenOnly = signal(false);
  protected readonly sort = signal<DriverSort>('BEST');
  protected readonly results = signal<DriverCard[] | null>(null);
  protected readonly busy = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly money = rupees;

  protected readonly isWoman = computed(() => this.auth.user()?.gender === 'FEMALE');
  protected readonly length = computed(() => tripLength(this.kind(), this.kind() === 'LOCAL' ? this.hours() : this.days()));
  protected readonly sorted = computed(() => {
    const list = this.results();
    if (!list) return null;
    const s = this.sort();
    if (s === 'BEST') return list;
    return [...list].sort((a, b) => s === 'PRICE' ? (a.fare ?? 0) - (b.fare ?? 0) : (a.distanceKm ?? 0) - (b.distanceKm ?? 0));
  });

  async ngOnInit(): Promise<void> {
    if (this.fromParam()) this.from.set(this.fromParam()!);
    if (this.kindParam() === 'OUTSTATION') this.kind.set('OUTSTATION');
    if (Number(this.hoursParam())) this.hours.set(Number(this.hoursParam()));
    if (Number(this.daysParam())) this.days.set(Number(this.daysParam()));
    if (this.automaticParam() === 'true') this.automatic.set(true);
    const id = Number(this.placeId());
    if (id && this.features.isOn('drivers.hire')) {
      try {
        this.place.set(await this.rides.place(id));
        await this.search();
      } catch {
        // The form still works.
      }
    }
  }

  protected linkParams(): Record<string, string | number | boolean> {
    return {
      placeId: this.place()?.id ?? '', from: this.from(), kind: this.kind(), automatic: this.automatic(),
      ...(this.kind() === 'LOCAL' ? { hours: this.hours() } : { days: this.days() }),
    };
  }

  protected async find(event: Event): Promise<void> {
    event.preventDefault();
    if (!this.place()) {
      this.error.set('Choose a town first.');
      return;
    }
    void this.router.navigate([], { queryParams: this.linkParams(), replaceUrl: true });
    await this.search();
  }

  private async search(): Promise<void> {
    const p = this.place();
    const from = fromLocalInput(this.from());
    if (!p || !from) return;
    this.busy.set(true);
    this.error.set(null);
    try {
      this.results.set(await this.api.search({
        placeId: p.id, kind: this.kind(), from, hours: this.hours(), days: this.days(), automatic: this.automatic(),
        womenOnly: this.womenOnly(), sort: 'BEST',
      }));
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
