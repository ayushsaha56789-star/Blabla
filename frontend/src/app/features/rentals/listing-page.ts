import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, effect, inject, input, signal, untracked } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { AuthService } from '../../core/auth.service';
import { problemOf } from '../../core/errors';
import { fromLocalInput, ListingDetail, Quote, RentalMode, RentalsApi, toLocalInput } from '../../core/rentals-api';
import { rupees } from '../../core/rides-format';
import { ToastService } from '../../core/toast.service';
import { Avatar } from '../../shared/avatar';
import { Icon } from '../../shared/icon';
import { Stars } from '../../shared/stars';

/** One car for rent: photos, terms, the owner, and booking it. */
@Component({
  selector: 'app-listing-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [DatePipe, RouterLink, Avatar, Icon, Stars],
  template: `
    <div class="container-page py-6 sm:py-10">
      <a routerLink="/rentals" class="btn btn-ghost btn-sm -ml-2"><app-icon name="arrowLeft" [size]="16" /> Rent a car</a>
      @if (error()) {
        <p class="card mt-4 p-6 text-center" role="alert">{{ error() }}</p>
      } @else if (listing(); as l) {
        <div class="mt-3 grid gap-6 lg:grid-cols-[1fr_24rem]">
          <div class="min-w-0">
            <div class="overflow-hidden rounded-3xl bg-slate-100 dark:bg-slate-800">
              @if (l.photoUrls.length) {
                <img [src]="l.photoUrls[photo()]" [alt]="l.car.name" class="aspect-[16/10] w-full object-cover" />
              } @else {
                <div class="flex aspect-[16/10] items-center justify-center text-slate-400"><app-icon name="car" [size]="56" /></div>
              }
            </div>
            @if (l.photoUrls.length > 1) {
              <div class="mt-2 flex gap-2 overflow-x-auto pb-1">
                @for (u of l.photoUrls; track u) {
                  <button type="button" class="h-16 w-24 shrink-0 overflow-hidden rounded-xl border-2" [class]="photo() === $index ? 'border-brand-500' : 'border-transparent'"
                          (click)="photo.set($index)" [attr.aria-label]="'Photo ' + ($index + 1)"><img [src]="u" alt="" class="h-full w-full object-cover" /></button>
                }
              </div>
            }
            <header class="mt-5 flex flex-wrap items-start justify-between gap-3">
              <div>
                <h1 class="text-2xl font-extrabold tracking-tight sm:text-3xl">{{ l.car.name }}</h1>
                <p class="mt-1 text-slate-600 dark:text-slate-400">{{ l.area }} · {{ l.placeName }}@if (l.state) {, {{ l.state }} }</p>
              </div>
              @if (l.verified) { <span class="badge badge-success"><app-icon name="shield" [size]="14" /> Documents checked</span> }
              @else { <span class="badge badge-muted">Documents not checked yet</span> }
            </header>
            <div class="mt-4 flex flex-wrap gap-2">
              @if (l.selfDrive) { <span class="chip">Self-drive</span> }
              @if (l.withDriver) { <span class="chip">With driver</span> }
              @if (l.car.seats) { <span class="chip">{{ l.car.seats }} seats</span> }
              @if (l.car.color) { <span class="chip">{{ l.car.color }}</span> }
              @if (l.instant) { <span class="chip"><app-icon name="sparkles" [size]="12" /> Instant booking</span> }
            </div>
            <dl class="card mt-5 grid gap-4 p-5 text-sm sm:grid-cols-2">
              <div><dt class="text-slate-500 dark:text-slate-400">Price</dt><dd class="font-semibold">{{ money(l.dayPrice) }} a day@if (l.hourPrice) { · {{ money(l.hourPrice) }} an hour (4 h minimum) }</dd></div>
              <div><dt class="text-slate-500 dark:text-slate-400">Kilometres</dt><dd class="font-semibold">{{ l.kmPerDay }} km a day included, then {{ money(l.extraKmPrice) }}/km</dd></div>
              <div><dt class="text-slate-500 dark:text-slate-400">Fuel</dt><dd class="font-semibold">{{ l.fuelPolicy === 'INCLUDED' ? 'Included' : 'Return it with the same level' }}</dd></div>
              @if (l.selfDrive) {
                <div><dt class="text-slate-500 dark:text-slate-400">Self-drive</dt><dd class="font-semibold">Deposit {{ money(l.deposit) }} · age {{ l.minAge }}+ · verified licence</dd></div>
              }
            </dl>
            @if (l.description) { <section class="mt-5"><h2 class="font-bold">About the car</h2><p class="mt-2 whitespace-pre-line text-slate-700 dark:text-slate-300">{{ l.description }}</p></section> }
            @if (l.rules) { <section class="mt-5"><h2 class="font-bold">House rules</h2><p class="mt-2 whitespace-pre-line text-slate-700 dark:text-slate-300">{{ l.rules }}</p></section> }
            @if (l.unavailable.length) {
              <section class="mt-5">
                <h2 class="font-bold">Already booked</h2>
                <ul class="mt-2 space-y-1 text-sm text-slate-600 dark:text-slate-400">
                  @for (u of l.unavailable; track $index) { <li>{{ u.from | date: 'd MMM, h:mm a' }} – {{ u.to | date: 'd MMM, h:mm a' }}</li> }
                </ul>
              </section>
            }
            <a [routerLink]="['/u', l.owner.id]" class="card mt-5 flex items-center gap-3 p-4">
              <app-avatar [url]="l.owner.avatarUrl" [name]="l.owner.displayName" [size]="48" />
              <span class="min-w-0 flex-1"><span class="block font-semibold">{{ l.owner.displayName }}</span>
                <span class="flex items-center gap-1 text-sm text-slate-500 dark:text-slate-400">Owner <app-stars [value]="l.owner.rating" [count]="l.owner.reviewCount" /></span></span>
              <app-icon name="arrowRight" [size]="18" class="text-slate-400" />
            </a>
          </div>

          <aside class="lg:sticky lg:top-24 lg:self-start">
            <section class="card p-5" aria-labelledby="book-heading">
              <h2 id="book-heading" class="text-lg font-bold">Book this car</h2>
              @if (l.mine) {
                <p class="mt-2 text-sm text-slate-600 dark:text-slate-400">This is your car.</p>
                <a [routerLink]="['/rentals/listings', l.id]" class="btn btn-secondary mt-3 w-full">Manage the listing</a>
              } @else {
                @if (l.selfDrive && l.withDriver) {
                  <div class="segmented mt-3 grid-cols-2" role="radiogroup" aria-label="Type">
                    <button type="button" role="radio" class="segment" [class.segment-on]="mode() === 'SELF_DRIVE'" [attr.aria-checked]="mode() === 'SELF_DRIVE'" (click)="setMode('SELF_DRIVE')">Self-drive</button>
                    <button type="button" role="radio" class="segment" [class.segment-on]="mode() === 'WITH_DRIVER'" [attr.aria-checked]="mode() === 'WITH_DRIVER'" (click)="setMode('WITH_DRIVER')">With driver</button>
                  </div>
                }
                <div class="mt-3 space-y-2">
                  <div><label for="l-from" class="label text-xs">Pickup</label>
                    <input id="l-from" type="datetime-local" class="input text-sm" [value]="from()" (input)="from.set($any($event.target).value); requote()" /></div>
                  <div><label for="l-to" class="label text-xs">Return</label>
                    <input id="l-to" type="datetime-local" class="input text-sm" [value]="to()" (input)="to.set($any($event.target).value); requote()" /></div>
                </div>
                @if (quote(); as q) {
                  @if (q.available) {
                    <dl class="mt-4 space-y-1.5 text-sm">
                      <div class="flex justify-between"><dt class="text-slate-600 dark:text-slate-400">Rent ({{ q.hourly ? q.hours + ' h' : days(q.hours) + (days(q.hours) === 1 ? ' day' : ' days') }})</dt><dd>{{ money(q.rent) }}</dd></div>
                      @if (q.deposit) { <div class="flex justify-between"><dt class="text-slate-600 dark:text-slate-400">Refundable deposit</dt><dd>{{ money(q.deposit) }}</dd></div> }
                      <div class="flex justify-between text-xs text-slate-500 dark:text-slate-400"><dt>{{ q.kmIncluded }} km included</dt><dd>then {{ money(q.extraKmPrice) }}/km</dd></div>
                      <div class="flex justify-between border-t border-slate-100 pt-2 dark:border-slate-800"><dt class="font-semibold">Total</dt><dd class="text-lg font-extrabold">{{ money(q.rent + q.deposit) }}</dd></div>
                    </dl>
                  } @else {
                    <p class="mt-4 rounded-2xl bg-accent-100/60 p-3 text-sm dark:bg-accent-600/10" role="status">{{ q.reason }}</p>
                  }
                }
                <label for="l-msg" class="label mt-4">Message to {{ l.owner.displayName }} <span class="font-normal text-slate-500">(optional)</span></label>
                <textarea id="l-msg" class="input min-h-16 text-sm" maxlength="300" [value]="message()" (input)="message.set($any($event.target).value)" placeholder="Hi! Where do I pick it up?"></textarea>
                <button type="button" class="btn btn-primary mt-4 w-full py-3" (click)="book()" [disabled]="busy() || !quote()?.available">
                  @if (!auth.isLoggedIn()) { Log in to book } @else if (busy()) { Sending… } @else if (l.instant) { <app-icon name="sparkles" [size]="18" /> Book now } @else { Request to book }
                </button>
                <p class="hint text-center">
                  @if (mode() === 'SELF_DRIVE') { Self-drive needs a verified driving licence (Profile → Verification). }
                  @if (l.instant) { Confirmed at once. } @else { The owner accepts or declines within a day. }
                  The deposit comes back after the return.
                </p>
              }
            </section>
          </aside>
        </div>
      } @else {
        <div class="skeleton mt-4 aspect-[16/10] max-w-3xl"></div>
      }
    </div>
  `,
})
export class ListingPage {
  private readonly api = inject(RentalsApi);
  private readonly router = inject(Router);
  private readonly toast = inject(ToastService);
  protected readonly auth = inject(AuthService);

  readonly id = input.required<string>();
  readonly fromParam = input<string | undefined>(undefined, { alias: 'from' });
  readonly toParam = input<string | undefined>(undefined, { alias: 'to' });
  readonly modeParam = input<string | undefined>(undefined, { alias: 'mode' });

  protected readonly listing = signal<ListingDetail | null>(null);
  protected readonly error = signal<string | null>(null);
  protected readonly photo = signal(0);
  protected readonly mode = signal<RentalMode>('SELF_DRIVE');
  protected readonly from = signal(toLocalInput(roundHour(3)));
  protected readonly to = signal(toLocalInput(new Date(roundHour(3).getTime() + 24 * 3600_000)));
  protected readonly quote = signal<Quote | null>(null);
  protected readonly message = signal('');
  protected readonly busy = signal(false);
  protected readonly money = rupees;
  protected readonly days = (hours: number) => Math.max(1, Math.ceil(hours / 24));
  private quoteTimer: ReturnType<typeof setTimeout> | null = null;

  protected readonly available = computed(() => this.quote()?.available ?? false);

  constructor() {
    effect(() => {
      const id = this.id();
      untracked(() => void this.load(id));
    });
  }

  protected setMode(m: RentalMode): void {
    this.mode.set(m);
    this.requote();
  }

  protected requote(): void {
    if (this.quoteTimer) clearTimeout(this.quoteTimer);
    this.quoteTimer = setTimeout(() => void this.fetchQuote(), 300);
  }

  protected async book(): Promise<void> {
    const l = this.listing();
    if (!l) return;
    if (!this.auth.isLoggedIn()) {
      void this.router.navigate(['/login'], { queryParams: { returnUrl: this.router.url } });
      return;
    }
    const from = fromLocalInput(this.from());
    const to = fromLocalInput(this.to());
    if (!from || !to) return;
    this.busy.set(true);
    try {
      const r = await this.api.rent(l.id, this.mode(), from, to, this.message().trim() || null);
      this.toast.success(r.status === 'ACCEPTED' ? 'Booked! Next: the details and paying.' : `Request sent. ${l.owner.displayName} will answer within a day.`);
      void this.router.navigate(['/rentals/bookings', r.id]);
    } catch (e) {
      const p = problemOf(e);
      this.toast.error(p.errors ? Object.values(p.errors)[0] : p.detail);
      if (p.code === 'licence_needed' || p.code === 'too_young') {
        void this.router.navigate(['/profile'], { fragment: 'verification' });
      }
    } finally {
      this.busy.set(false);
    }
  }

  private async load(id: string): Promise<void> {
    try {
      const l = await this.api.listing(id);
      this.listing.set(l);
      const m = this.modeParam();
      this.mode.set(m === 'WITH_DRIVER' || !l.selfDrive ? 'WITH_DRIVER' : 'SELF_DRIVE');
      if (this.fromParam()) this.from.set(this.fromParam()!);
      if (this.toParam()) this.to.set(this.toParam()!);
      if (!l.mine) await this.fetchQuote();
    } catch (e) {
      this.error.set(problemOf(e).detail);
    }
  }

  private async fetchQuote(): Promise<void> {
    const l = this.listing();
    const from = fromLocalInput(this.from());
    const to = fromLocalInput(this.to());
    if (!l || !from || !to) {
      this.quote.set(null);
      return;
    }
    try {
      this.quote.set(await this.api.quote(l.id, from, to, this.mode()));
    } catch (e) {
      this.quote.set({ available: false, reason: problemOf(e).detail, rent: 0, deposit: 0, kmIncluded: 0, extraKmPrice: 0, hours: 0, hourly: false });
    }
  }
}

function roundHour(hours: number): Date {
  const d = new Date(Date.now() + hours * 3600_000);
  d.setMinutes(0, 0, 0);
  return d;
}
