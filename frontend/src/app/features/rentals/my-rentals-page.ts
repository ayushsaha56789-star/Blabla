import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, inject, input, OnInit, signal } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { problemOf } from '../../core/errors';
import { MyListing, RentalsApi, rentalStatus, RentalView } from '../../core/rentals-api';
import { rupees } from '../../core/rides-format';
import { Icon } from '../../shared/icon';

type Tab = 'renting' | 'lending' | 'cars';

const LISTING_STATUS: Record<string, { text: string; tone: string }> = {
  DRAFT: { text: 'Draft', tone: 'muted' },
  IN_REVIEW: { text: 'Being checked', tone: 'warning' },
  LIVE: { text: 'Live', tone: 'success' },
  PAUSED: { text: 'Paused', tone: 'muted' },
  REJECTED: { text: 'Needs changes', tone: 'danger' },
};

/** Rentals: cars I'm renting, my cars rented by others, and my listings. */
@Component({
  selector: 'app-my-rentals-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [DatePipe, RouterLink, Icon],
  template: `
    <div class="container-page max-w-4xl py-8 sm:py-10">
      <header class="flex flex-wrap items-end justify-between gap-4">
        <div>
          <p class="kicker">Rent a car</p>
          <h1 class="mt-1 text-3xl font-extrabold tracking-tight">My rentals</h1>
        </div>
        <a routerLink="/rentals/new" class="btn btn-primary btn-sm"><app-icon name="plus" [size]="16" /> List a car</a>
      </header>
      <div class="segmented mt-6 grid-cols-3" role="tablist" aria-label="Show">
        @for (t of tabs; track t.key) {
          <button type="button" role="tab" class="segment" [class.segment-on]="tab() === t.key" [attr.aria-selected]="tab() === t.key" (click)="show(t.key)">
            {{ t.label }}@if (t.key === 'lending' && waiting()) { <span class="ml-1 rounded-full bg-accent-500 px-1.5 text-[0.7rem] leading-5 font-bold text-white">{{ waiting() }}</span> }
          </button>
        }
      </div>

      @if (error()) { <p class="card mt-6 p-6 text-center" role="alert">{{ error() }}</p> }

      @if (tab() === 'cars') {
        @if (listings(); as list) {
          @if (!list.length) {
            <div class="card mt-6 p-10 text-center">
              <app-icon name="car" [size]="32" class="text-slate-400" />
              <p class="mt-3 font-semibold">Earn from your car when you don't need it.</p>
              <p class="mt-1 text-sm text-slate-600 dark:text-slate-400">List it for self-drive, or drive it yourself. You choose the price, the dates and who rents it.</p>
              <a routerLink="/rentals/new" class="btn btn-primary mt-4">List a car</a>
            </div>
          } @else {
            <ul class="mt-6 space-y-3">
              @for (m of list; track m.listing.id) {
                <li>
                  <a [routerLink]="['/rentals/listings', m.listing.id]" class="card flex items-center gap-4 p-4 transition hover:shadow-md">
                    <span class="h-16 w-24 shrink-0 overflow-hidden rounded-xl bg-slate-100 dark:bg-slate-800">
                      @if (m.photos.length) { <img [src]="m.photos[0].url" alt="" class="h-full w-full object-cover" /> }
                    </span>
                    <span class="min-w-0 flex-1">
                      <span class="block truncate font-semibold">{{ m.listing.car.name }}</span>
                      <span class="block text-sm text-slate-500 dark:text-slate-400">{{ money(m.listing.dayPrice) }}/day · {{ m.listing.placeName }}</span>
                      @if (m.openRequests) { <span class="mt-1 inline-block rounded-full bg-accent-500 px-2 text-xs leading-5 font-bold text-white">{{ m.openRequests }} {{ m.openRequests === 1 ? 'request' : 'requests' }}</span> }
                    </span>
                    <span class="badge" [class]="'tone-' + listingStatus(m.listing.status).tone">{{ listingStatus(m.listing.status).text }}</span>
                  </a>
                </li>
              }
            </ul>
          }
        } @else if (!error()) { <div class="skeleton mt-6 h-24"></div> }
      } @else {
        @if (rentals(); as list) {
          @if (!list.length) {
            <div class="card mt-6 p-10 text-center">
              <p class="font-semibold">{{ tab() === 'renting' ? "You haven't rented a car yet." : 'Nobody has rented your cars yet.' }}</p>
              <a [routerLink]="tab() === 'renting' ? '/rentals' : '/rentals/new'" class="btn btn-secondary btn-sm mt-4">{{ tab() === 'renting' ? 'Find a car' : 'List a car' }}</a>
            </div>
          } @else {
            <ul class="mt-6 space-y-3">
              @for (r of list; track r.id) {
                <li>
                  <a [routerLink]="['/rentals/bookings', r.id]" class="card flex items-center gap-4 p-4 transition hover:shadow-md">
                    <span class="h-16 w-24 shrink-0 overflow-hidden rounded-xl bg-slate-100 dark:bg-slate-800">
                      @if (r.listing.photoUrl) { <img [src]="r.listing.photoUrl" alt="" class="h-full w-full object-cover" /> }
                    </span>
                    <span class="min-w-0 flex-1">
                      <span class="block truncate font-semibold">{{ r.listing.car.name }}</span>
                      <span class="block text-sm text-slate-500 dark:text-slate-400">{{ r.from | date: 'd MMM, h:mm a' }} – {{ r.to | date: 'd MMM, h:mm a' }}</span>
                      <span class="block text-xs text-slate-500 dark:text-slate-400">{{ tab() === 'renting' ? 'From ' + r.owner.displayName : 'By ' + r.renter.displayName }} · {{ money(r.rent) }}</span>
                    </span>
                    <span class="badge shrink-0" [class]="'tone-' + status(r).tone">{{ status(r).text }}</span>
                  </a>
                </li>
              }
            </ul>
          }
        } @else if (!error()) { <div class="skeleton mt-6 h-24"></div> }
      }
    </div>
  `,
})
export class MyRentalsPage implements OnInit {
  private readonly api = inject(RentalsApi);
  private readonly router = inject(Router);

  readonly tabParam = input<string | undefined>(undefined, { alias: 'tab' });

  protected readonly tabs: { key: Tab; label: string }[] = [
    { key: 'renting', label: 'Renting' }, { key: 'lending', label: 'Lending' }, { key: 'cars', label: 'My cars' },
  ];
  protected readonly tab = signal<Tab>('renting');
  protected readonly rentals = signal<RentalView[] | null>(null);
  protected readonly listings = signal<MyListing[] | null>(null);
  protected readonly waiting = signal(0);
  protected readonly error = signal<string | null>(null);
  protected readonly money = rupees;
  protected readonly status = rentalStatus;
  private all: { renting: RentalView[]; lending: RentalView[] } | null = null;

  async ngOnInit(): Promise<void> {
    const t = this.tabParam();
    if (t === 'lending' || t === 'cars' || t === 'renting') this.tab.set(t);
    await this.load();
  }

  protected listingStatus(s: string): { text: string; tone: string } {
    return LISTING_STATUS[s] ?? { text: s, tone: 'muted' };
  }

  protected show(t: Tab): void {
    this.tab.set(t);
    void this.router.navigate([], { queryParams: { tab: t }, replaceUrl: true });
    void this.load();
  }

  private async load(): Promise<void> {
    try {
      if (this.tab() === 'cars') {
        this.listings.set(await this.api.myListings());
      } else {
        this.all ??= await this.api.myRentals();
        this.waiting.set(this.all.lending.filter((r) => r.status === 'REQUESTED').length);
        this.rentals.set(this.tab() === 'renting' ? this.all.renting : this.all.lending);
      }
      this.error.set(null);
    } catch (e) {
      this.error.set(problemOf(e).detail);
    }
  }
}
