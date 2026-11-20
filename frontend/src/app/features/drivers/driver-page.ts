import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, effect, inject, input, signal, untracked } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { AuthService } from '../../core/auth.service';
import { DriverDetail, DriverQuote, DriversApi, fareLines, LICENCE_LABELS, TripKind, tripLength } from '../../core/drivers-api';
import { problemOf } from '../../core/errors';
import { FeaturesService } from '../../core/features.service';
import { fromLocalInput, toLocalInput } from '../../core/rentals-api';
import { rupees } from '../../core/rides-format';
import { RidesApi } from '../../core/rides-api';
import { Place } from '../../core/rides.models';
import { ToastService } from '../../core/toast.service';
import { Avatar } from '../../shared/avatar';
import { Icon } from '../../shared/icon';
import { PlaceInput } from '../../shared/place-input';
import { Stars } from '../../shared/stars';

/** One driver partner: who they are, where they work, their prices and reviews, and booking them. */
@Component({
  selector: 'app-driver-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [DatePipe, RouterLink, Avatar, Icon, PlaceInput, Stars],
  template: `
    <div class="container-page py-6 sm:py-10">
      <a routerLink="/drivers" class="btn btn-ghost btn-sm -ml-2"><app-icon name="arrowLeft" [size]="16" /> Hire a driver</a>
      @if (error()) {
        <p class="card mt-4 p-6 text-center" role="alert">{{ error() }}</p>
      } @else if (detail(); as d) {
        <div class="mt-3 grid gap-6 lg:grid-cols-[1fr_24rem]">
          <div class="min-w-0">
            <header class="card flex flex-wrap items-center gap-4 p-5 sm:p-6">
              <app-avatar [url]="d.driver.member.avatarUrl" [name]="d.driver.member.displayName" [size]="80" />
              <div class="min-w-0 flex-1">
                <h1 class="text-2xl font-extrabold tracking-tight sm:text-3xl">{{ d.driver.member.displayName }}</h1>
                <p class="mt-1 flex flex-wrap items-center gap-x-2 text-slate-600 dark:text-slate-400">
                  @if (d.driver.reviewCount) { <app-stars [value]="d.driver.rating" [count]="d.driver.reviewCount" /> <span aria-hidden="true">·</span> }
                  <span>{{ d.driver.tripsDone }} {{ d.driver.tripsDone === 1 ? 'trip' : 'trips' }} · {{ d.driver.experienceYears }} {{ d.driver.experienceYears === 1 ? 'year' : 'years' }} driving</span></p>
                <div class="mt-2 flex flex-wrap gap-1.5">
                  <span class="badge badge-success"><app-icon name="idCard" [size]="12" /> Licence checked</span>
                  @if (d.driver.policeChecked) { <span class="badge badge-success"><app-icon name="shield" [size]="12" /> Police verified</span> }
                  <span class="chip">{{ licenceLabels[d.licenceClass] }}</span>
                </div>
              </div>
            </header>

            @if (d.about) { <section class="mt-5"><h2 class="font-bold">About</h2><p class="mt-2 whitespace-pre-line text-slate-700 dark:text-slate-300">{{ d.about }}</p></section> }

            <dl class="card mt-5 grid gap-4 p-5 text-sm sm:grid-cols-2">
              <div><dt class="text-slate-500 dark:text-slate-400">In town</dt><dd class="font-semibold">{{ money(d.driver.rates.hourPrice) }} an hour, at most {{ money(d.driver.rates.dayPrice) }} for a day (12 h)</dd></div>
              @if (d.driver.outstation) {
                <div><dt class="text-slate-500 dark:text-slate-400">Outstation</dt><dd class="font-semibold">{{ money(d.driver.rates.outstationPrice) }} a day@if (d.driver.rates.allowance) { + {{ money(d.driver.rates.allowance) }} a day for food and stay }</dd></div>
              } @else {
                <div><dt class="text-slate-500 dark:text-slate-400">Outstation</dt><dd class="font-semibold">Not taken</dd></div>
              }
              <div><dt class="text-slate-500 dark:text-slate-400">Drives</dt><dd class="font-semibold">{{ d.driver.manual && d.driver.automatic ? 'Manual and automatic cars' : d.driver.automatic ? 'Automatic cars' : 'Manual cars' }}</dd></div>
              @if (d.driver.rates.nightCharge) { <div><dt class="text-slate-500 dark:text-slate-400">Late trips</dt><dd class="font-semibold">+{{ money(d.driver.rates.nightCharge) }} between 10 pm and 6 am</dd></div> }
              @if (d.driver.languages.length) { <div><dt class="text-slate-500 dark:text-slate-400">Speaks</dt><dd class="font-semibold">{{ d.driver.languages.join(', ') }}</dd></div> }
              <div><dt class="text-slate-500 dark:text-slate-400">Works in</dt><dd class="font-semibold">@for (a of d.areas; track a.id) { <span>{{ a.placeName }} ({{ a.radiusKm }} km around)@if (!$last) {, }</span> }</dd></div>
            </dl>
            <p class="hint">Fuel, tolls and parking are yours, as it's your car.</p>

            <section class="mt-6" aria-labelledby="reviews-heading">
              <h2 id="reviews-heading" class="font-bold">What members say</h2>
              @if (!d.reviews.length) { <p class="mt-2 text-sm text-slate-500 dark:text-slate-400">No reviews yet.</p> }
              <ul class="mt-2 space-y-2">
                @for (r of d.reviews; track $index) {
                  <li class="card p-4 text-sm">
                    <p class="flex items-center justify-between gap-2"><span class="font-semibold">{{ r.authorName }}</span><span class="text-xs text-slate-500">{{ r.at | date: 'd MMM y' }}</span></p>
                    <p class="mt-1 text-amber-500" [attr.aria-label]="r.stars + ' stars'">{{ '★'.repeat(r.stars) }}<span class="text-slate-300 dark:text-slate-600">{{ '★'.repeat(5 - r.stars) }}</span></p>
                    @if (r.comment) { <p class="mt-1 text-slate-700 dark:text-slate-300">{{ r.comment }}</p> }
                  </li>
                }
              </ul>
            </section>
          </div>

          <aside class="lg:sticky lg:top-24 lg:self-start">
            <section class="card p-5" aria-labelledby="hire-heading">
              <h2 id="hire-heading" class="text-lg font-bold">Hire {{ d.driver.member.displayName }}</h2>
              @if (d.mine) {
                <p class="mt-2 text-sm text-slate-600 dark:text-slate-400">This is you. <a routerLink="/drivers/join" class="font-semibold text-brand-700 underline dark:text-brand-400">Your driver profile</a></p>
              } @else if (!features.isOn('drivers.hire')) {
                <p class="mt-2 text-sm text-slate-600 dark:text-slate-400">Hiring isn't open right now.</p>
              } @else {
                @if (d.driver.outstation) {
                  <div class="segmented mt-3 grid-cols-2" role="radiogroup" aria-label="Trip">
                    <button type="button" role="radio" class="segment" [class.segment-on]="kind() === 'LOCAL'" [attr.aria-checked]="kind() === 'LOCAL'" (click)="setKind('LOCAL')">In town</button>
                    <button type="button" role="radio" class="segment" [class.segment-on]="kind() === 'OUTSTATION'" [attr.aria-checked]="kind() === 'OUTSTATION'" (click)="setKind('OUTSTATION')">Outstation</button>
                  </div>
                }
                <div class="mt-3 space-y-3">
                  <div><label class="label text-xs" for="h-place">Pickup town</label>
                    <app-place-input inputId="h-place" [value]="place()" (valueChange)="place.set($event); requote()" placeholder="Town or city" /></div>
                  <div><label class="label text-xs" for="h-from">When</label>
                    <input id="h-from" type="datetime-local" class="input text-sm" [value]="from()" (input)="from.set($any($event.target).value); requote()" /></div>
                  @if (kind() === 'LOCAL') {
                    <div><label class="label text-xs" for="h-hours">How long</label>
                      <select id="h-hours" class="input text-sm" (change)="hours.set(+$any($event.target).value); requote()">
                        @for (h of hourChoices; track h) { <option [value]="h" [selected]="h === hours()">{{ h }} hours</option> }
                      </select></div>
                  } @else {
                    <div><label class="label text-xs" for="h-days">How many days</label>
                      <select id="h-days" class="input text-sm" (change)="days.set(+$any($event.target).value); requote()">
                        @for (n of dayChoices; track n) { <option [value]="n" [selected]="n === days()">{{ n }} {{ n === 1 ? 'day' : 'days' }}</option> }
                      </select></div>
                    <div><label class="label text-xs" for="h-dest">Going to</label>
                      <app-place-input inputId="h-dest" [(value)]="destination" placeholder="Destination town" /></div>
                  }
                  <div class="segmented grid-cols-2" role="radiogroup" aria-label="Your car">
                    <button type="button" role="radio" class="segment" [class.segment-on]="!automatic()" [attr.aria-checked]="!automatic()" (click)="automatic.set(false); requote()">Manual</button>
                    <button type="button" role="radio" class="segment" [class.segment-on]="automatic()" [attr.aria-checked]="automatic()" (click)="automatic.set(true); requote()">Automatic</button>
                  </div>
                  <div><label class="label text-xs" for="h-car">Your car</label>
                    <input id="h-car" class="input text-sm" maxlength="100" [value]="car()" (input)="car.set($any($event.target).value)" placeholder="e.g. White Maruti Swift" /></div>
                  <div><label class="label text-xs" for="h-address">Pickup address</label>
                    <input id="h-address" class="input text-sm" maxlength="300" [value]="address()" (input)="address.set($any($event.target).value)" placeholder="House, street, landmark" />
                    <p class="hint">Shown to the driver only after they accept.</p></div>
                </div>

                @if (quote(); as q) {
                  @if (q.available) {
                    <dl class="mt-4 space-y-1.5 text-sm">
                      @for (line of lines(); track line.label) {
                        <div class="flex justify-between gap-2"><dt class="text-slate-600 dark:text-slate-400">{{ line.label }}</dt><dd>{{ money(line.amount) }}</dd></div>
                      }
                      <div class="flex justify-between border-t border-slate-100 pt-2 dark:border-slate-800"><dt class="font-semibold">Total for {{ length() }}</dt><dd class="text-lg font-extrabold">{{ money(q.fare) }}</dd></div>
                      <div class="flex justify-between text-xs text-slate-500 dark:text-slate-400"><dt>Extra {{ kind() === 'LOCAL' ? 'hour' : 'day' }}, if the trip runs over</dt><dd>{{ money(q.extraPrice) }}</dd></div>
                    </dl>
                  } @else {
                    <p class="mt-4 rounded-2xl bg-accent-100/60 p-3 text-sm dark:bg-accent-600/10" role="status">{{ q.problem }}</p>
                  }
                }
                <label for="h-msg" class="label mt-4">Message <span class="font-normal text-slate-500">(optional)</span></label>
                <textarea id="h-msg" class="input min-h-16 text-sm" maxlength="300" [value]="message()" (input)="message.set($any($event.target).value)" placeholder="e.g. Airport drop and back, two stops on the way"></textarea>
                <button type="button" class="btn btn-primary mt-4 w-full py-3" (click)="hire()" [disabled]="busy() || (auth.isLoggedIn() && !quote()?.available)">
                  @if (!auth.isLoggedIn()) { Log in to hire } @else if (busy()) { Sending… } @else { Request {{ d.driver.member.displayName }} }
                </button>
                <p class="hint text-center">The driver answers within 3 hours. Phone numbers are shared once they accept.</p>
              }
            </section>
          </aside>
        </div>
      } @else {
        <div class="skeleton mt-4 h-60 max-w-3xl"></div>
      }
    </div>
  `,
})
export class DriverPage {
  private readonly api = inject(DriversApi);
  private readonly rides = inject(RidesApi);
  private readonly router = inject(Router);
  private readonly toast = inject(ToastService);
  protected readonly auth = inject(AuthService);
  protected readonly features = inject(FeaturesService);

  readonly id = input.required<string>();
  readonly placeId = input<string | undefined>(undefined);
  readonly fromParam = input<string | undefined>(undefined, { alias: 'from' });
  readonly kindParam = input<string | undefined>(undefined, { alias: 'kind' });
  readonly hoursParam = input<string | undefined>(undefined, { alias: 'hours' });
  readonly daysParam = input<string | undefined>(undefined, { alias: 'days' });
  readonly automaticParam = input<string | undefined>(undefined, { alias: 'automatic' });

  protected readonly hourChoices = [2, 3, 4, 5, 6, 8, 10, 12];
  protected readonly dayChoices = Array.from({ length: 15 }, (_, i) => i + 1);
  protected readonly licenceLabels = LICENCE_LABELS;
  protected readonly detail = signal<DriverDetail | null>(null);
  protected readonly error = signal<string | null>(null);
  protected readonly kind = signal<TripKind>('LOCAL');
  protected readonly place = signal<Place | null>(null);
  protected readonly destination = signal<Place | null>(null);
  protected readonly from = signal(toLocalInput(roundHour(3)));
  protected readonly hours = signal(4);
  protected readonly days = signal(1);
  protected readonly automatic = signal(false);
  protected readonly car = signal('');
  protected readonly address = signal('');
  protected readonly message = signal('');
  protected readonly quote = signal<DriverQuote | null>(null);
  protected readonly busy = signal(false);
  protected readonly money = rupees;
  private quoteTimer: ReturnType<typeof setTimeout> | null = null;

  protected lines() {
    const q = this.quote();
    const d = this.detail();
    return q && d ? fareLines(q, d.driver.rates) : [];
  }

  protected length(): string {
    return tripLength(this.kind(), this.kind() === 'LOCAL' ? this.hours() : this.days());
  }

  constructor() {
    effect(() => {
      const id = this.id();
      untracked(() => void this.load(id));
    });
  }

  protected setKind(k: TripKind): void {
    this.kind.set(k);
    this.requote();
  }

  protected requote(): void {
    if (this.quoteTimer) clearTimeout(this.quoteTimer);
    this.quoteTimer = setTimeout(() => void this.fetchQuote(), 300);
  }

  protected async hire(): Promise<void> {
    const d = this.detail();
    if (!d) return;
    if (!this.auth.isLoggedIn()) {
      void this.router.navigate(['/login'], { queryParams: { returnUrl: this.router.url } });
      return;
    }
    const from = fromLocalInput(this.from());
    const place = this.place();
    if (!from || !place) {
      this.toast.error('Choose the pickup town and time.');
      return;
    }
    this.busy.set(true);
    try {
      const t = await this.api.hire({
        driverId: d.driver.id, kind: this.kind(), from, hours: this.kind() === 'LOCAL' ? this.hours() : null,
        days: this.kind() === 'OUTSTATION' ? this.days() : null, placeId: place.id, pickupAddress: this.address().trim(),
        destinationId: this.kind() === 'OUTSTATION' ? this.destination()?.id ?? null : null, car: this.car().trim(),
        automatic: this.automatic(), message: this.message().trim() || null,
      });
      this.toast.success(`Request sent. ${d.driver.member.displayName} will answer within 3 hours.`);
      void this.router.navigate(['/drivers/trips', t.id]);
    } catch (e) {
      const p = problemOf(e);
      this.toast.error(p.errors ? Object.values(p.errors)[0] : p.detail);
    } finally {
      this.busy.set(false);
    }
  }

  private async load(id: string): Promise<void> {
    try {
      const d = await this.api.profile(id);
      this.detail.set(d);
      if (this.fromParam()) this.from.set(this.fromParam()!);
      if (this.kindParam() === 'OUTSTATION' && d.driver.outstation) this.kind.set('OUTSTATION');
      if (Number(this.hoursParam())) this.hours.set(Number(this.hoursParam()));
      if (Number(this.daysParam())) this.days.set(Number(this.daysParam()));
      if (this.automaticParam() === 'true' && d.driver.automatic) this.automatic.set(true);
      if (!d.driver.manual) this.automatic.set(true);
      const placeId = Number(this.placeId()) || d.areas[0]?.placeId;
      if (placeId) this.place.set(await this.rides.place(placeId));
      if (!d.mine) await this.fetchQuote();
    } catch (e) {
      this.error.set(problemOf(e).detail);
    }
  }

  private async fetchQuote(): Promise<void> {
    const d = this.detail();
    const from = fromLocalInput(this.from());
    const place = this.place();
    if (!d || !from || !place || !this.features.isOn('drivers.hire')) {
      this.quote.set(null);
      return;
    }
    try {
      this.quote.set(await this.api.quote(d.driver.id, {
        placeId: place.id, kind: this.kind(), from, hours: this.hours(), days: this.days(), automatic: this.automatic(),
      }));
    } catch (e) {
      this.quote.set({ available: false, problem: problemOf(e).detail, kind: this.kind(), from, to: null, units: 0, base: 0,
        dayPriceApplied: false, night: 0, allowance: 0, fare: 0, extraPrice: 0 });
    }
  }
}

function roundHour(hours: number): Date {
  const d = new Date(Date.now() + hours * 3600_000);
  d.setMinutes(0, 0, 0);
  return d;
}
