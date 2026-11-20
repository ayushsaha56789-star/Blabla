import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, inject, OnInit, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { problemOf } from '../../core/errors';
import { AdminListingRow, DOCUMENT_LABELS, FUEL_LABELS, rentalStatus, RentalView } from '../../core/rentals-api';
import { rupees } from '../../core/rides-format';
import { ToastService } from '../../core/toast.service';
import { AuthImage } from '../../shared/auth-image';
import { Icon } from '../../shared/icon';
import { AdminApi } from '../admin-api';

type Tab = 'review' | 'disputes' | 'bookings';

/** Rentals: check listings' documents, settle disputes, see bookings. */
@Component({
  selector: 'app-admin-rentals',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [DatePipe, RouterLink, AuthImage, Icon],
  template: `
    <header class="flex flex-wrap items-end justify-between gap-4">
      <div>
        <p class="kicker">Car rentals</p>
        <h1 class="mt-1 text-3xl font-extrabold tracking-tight">Rentals</h1>
        <p class="mt-1 text-slate-600 dark:text-slate-400">Check that the documents match the car and the owner, and settle disputes about charges.</p>
      </div>
      <div class="segmented grid-cols-3" role="tablist" aria-label="Show">
        @for (t of tabs; track t.key) {
          <button type="button" role="tab" class="segment px-3 sm:px-4" [class.segment-on]="tab() === t.key" [attr.aria-selected]="tab() === t.key" (click)="show(t.key)">{{ t.label }}</button>
        }
      </div>
    </header>

    @if (error()) { <p class="field-error mt-4" role="alert">{{ error() }}</p> }

    @if (tab() === 'review') {
      @if (listings(); as list) {
        @if (!list.length) {
          <div class="card mt-6 p-10 text-center"><app-icon name="checkCircle" [size]="32" class="text-brand-600" /><p class="mt-3 font-semibold">No listings waiting to be checked.</p></div>
        } @else {
          <ul class="mt-6 space-y-4">
            @for (row of list; track row.listing.id) {
              <li class="card p-5">
                <div class="flex flex-wrap items-start gap-4">
                  @if (row.listing.photoUrls.length) { <img [src]="row.listing.photoUrls[0]" alt="" class="h-20 w-28 rounded-xl object-cover" /> }
                  <div class="min-w-0 flex-1">
                    <p class="font-bold">{{ row.listing.car.name }} @if (row.listing.car.color) { · {{ row.listing.car.color }} }</p>
                    <p class="text-sm text-slate-600 dark:text-slate-400">{{ row.listing.area }}, {{ row.listing.placeName }} · {{ money(row.listing.dayPrice) }}/day
                      · {{ row.listing.selfDrive ? 'self-drive' : '' }}{{ row.listing.selfDrive && row.listing.withDriver ? ' + ' : '' }}{{ row.listing.withDriver ? 'with driver' : '' }}</p>
                    <a [routerLink]="['/admin/members', row.listing.owner.id]" class="text-sm font-semibold text-brand-700 hover:underline dark:text-brand-400">{{ row.listing.owner.displayName }}</a>
                    @if (row.pickupAddress) { <p class="text-xs text-slate-500 dark:text-slate-400">Pickup: {{ row.pickupAddress }}</p> }
                  </div>
                  <span class="text-xs text-slate-500">{{ row.updatedAt | date: 'd MMM, h:mm a' }}</span>
                </div>
                <div class="mt-4 grid gap-3 sm:grid-cols-3">
                  @for (d of row.documents; track d.id) {
                    <figure>
                      <app-auth-img [src]="'/api/admin/rentals/listings/' + row.listing.id + '/documents/' + d.id" [alt]="docLabels[d.kind]" class="h-48 w-full" fit="contain" />
                      <figcaption class="mt-1 text-xs text-slate-500 dark:text-slate-400">{{ docLabels[d.kind] }} · viewing is recorded</figcaption>
                    </figure>
                  }
                </div>
                <div class="mt-4 flex flex-wrap items-end gap-2 border-t border-slate-100 pt-4 dark:border-slate-800">
                  <button type="button" class="btn btn-primary btn-sm" (click)="approve(row)" [disabled]="busy()"><app-icon name="check" [size]="16" /> Approve</button>
                  <label class="min-w-56 flex-1 text-sm"><span class="label text-xs">Or send it back: what should the owner fix?</span>
                    <input class="input py-2 text-sm" maxlength="300" [value]="notes()[row.listing.id] ?? ''" (input)="note(row.listing.id, $any($event.target).value)" placeholder="The RC photo is blurry" /></label>
                  <button type="button" class="btn btn-ghost btn-sm text-rose-600 dark:text-rose-400" (click)="reject(row)" [disabled]="busy() || (notes()[row.listing.id] ?? '').trim().length < 3">Send back</button>
                </div>
              </li>
            }
          </ul>
        }
      } @else if (!error()) { <div class="skeleton mt-6 h-40"></div> }
    } @else {
      @if (bookings(); as list) {
        @if (!list.length) {
          <div class="card mt-6 p-10 text-center"><app-icon name="checkCircle" [size]="32" class="text-brand-600" /><p class="mt-3 font-semibold">{{ tab() === 'disputes' ? 'No disputes to settle.' : 'No rentals yet.' }}</p></div>
        } @else {
          <ul class="mt-6 space-y-4">
            @for (r of list; track r.id) {
              <li class="card p-5 text-sm">
                <div class="flex flex-wrap items-center gap-2">
                  <span class="font-bold">{{ r.listing.car.name }}</span>
                  <span class="text-slate-500 dark:text-slate-400">{{ r.renter.displayName }} from {{ r.owner.displayName }} · {{ r.from | date: 'd MMM' }} – {{ r.to | date: 'd MMM' }}</span>
                  <span class="badge ml-auto" [class]="'tone-' + state(r).tone">{{ state(r).text }}</span>
                </div>
                <p class="mt-1 text-slate-600 dark:text-slate-400">Rent {{ money(r.rent) }} · deposit {{ money(r.deposit) }}@if (r.kmDriven !== null) { · {{ r.kmDriven }} km driven ({{ r.kmIncluded }} included) } · {{ r.payOnline ? 'paid online' : 'paid at pickup' }}</p>
                @if (r.status === 'DISPUTED' || r.extraCharges) {
                  <p class="mt-2">Owner's charges: <b>{{ money(r.extraCharges) }}</b>@if (r.extraKmCharge) { ({{ money(r.extraKmCharge) }} extra km) }@if (r.extraNote) { · “{{ r.extraNote }}” }</p>
                }
                @if (r.disputeNote) { <p class="mt-1">Renter says: “{{ r.disputeNote }}”</p> }
                @if (r.resolution) { <p class="mt-1 text-brand-700 dark:text-brand-400">Decided: {{ r.resolution }}</p> }
                @if (r.checks.length) {
                  <details class="mt-3"><summary class="cursor-pointer font-semibold">Pickup and return records ({{ r.checks.length }})</summary>
                    @for (c of r.checks; track $index) {
                      <div class="mt-2"><p>{{ c.stage === 'PICKUP' ? 'Pickup' : 'Return' }} by the {{ c.byRole === 'OWNER' ? 'owner' : 'renter' }}: {{ c.odometer }} km, {{ fuelLabels[c.fuel] }}@if (c.notes) { · {{ c.notes }} }</p>
                        <div class="mt-1 grid grid-cols-4 gap-2 sm:grid-cols-6">
                          @for (u of c.photoUrls; track u) { <app-auth-img [src]="staffPhoto(u)" alt="Photo of the car" class="h-16 w-full" /> }
                        </div></div>
                    }
                  </details>
                }
                @if (r.status === 'DISPUTED') {
                  <div class="mt-4 flex flex-wrap items-end gap-2 border-t border-slate-100 pt-4 dark:border-slate-800">
                    <label class="w-32 text-sm"><span class="label text-xs">Owner keeps (₹)</span>
                      <input class="input py-2 text-sm" type="number" min="0" [max]="r.deposit" [value]="charges()[r.id] ?? r.extraCharges" (input)="charge(r.id, $any($event.target).value)" /></label>
                    <label class="min-w-56 flex-1 text-sm"><span class="label text-xs">Decision, as both will read it</span>
                      <input class="input py-2 text-sm" maxlength="300" [value]="notes()[r.id] ?? ''" (input)="note(r.id, $any($event.target).value)" /></label>
                    <button type="button" class="btn btn-primary btn-sm" (click)="resolve(r)" [disabled]="busy() || (notes()[r.id] ?? '').trim().length < 5">Settle</button>
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
export class RentalsAdminPage implements OnInit {
  private readonly api = inject(AdminApi);
  private readonly toast = inject(ToastService);

  protected readonly tabs: { key: Tab; label: string }[] = [
    { key: 'review', label: 'To check' }, { key: 'disputes', label: 'Disputes' }, { key: 'bookings', label: 'All rentals' },
  ];
  protected readonly tab = signal<Tab>('review');
  protected readonly listings = signal<AdminListingRow[] | null>(null);
  protected readonly bookings = signal<RentalView[] | null>(null);
  protected readonly notes = signal<Record<string, string>>({});
  protected readonly charges = signal<Record<string, string>>({});
  protected readonly busy = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly money = rupees;
  protected readonly docLabels = DOCUMENT_LABELS;
  protected readonly fuelLabels = FUEL_LABELS;
  protected readonly state = rentalStatus;

  ngOnInit(): void {
    void this.load();
  }

  protected show(t: Tab): void {
    this.tab.set(t);
    this.listings.set(null);
    this.bookings.set(null);
    void this.load();
  }

  /** Member photo addresses become the console's (audited) ones. */
  protected staffPhoto(url: string): string {
    return url.replace('/api/rentals/', '/api/admin/rentals/');
  }

  protected note(id: string, value: string): void {
    this.notes.update((n) => ({ ...n, [id]: value }));
  }

  protected charge(id: string, value: string): void {
    this.charges.update((c) => ({ ...c, [id]: value }));
  }

  protected async approve(row: AdminListingRow): Promise<void> {
    await this.act(() => this.api.approveListing(row.listing.id), 'Approved: the car is live and shows as verified.');
  }

  protected async reject(row: AdminListingRow): Promise<void> {
    await this.act(() => this.api.rejectListing(row.listing.id, (this.notes()[row.listing.id] ?? '').trim()), 'Sent back to the owner.');
  }

  protected async resolve(r: RentalView): Promise<void> {
    const charges = Number(this.charges()[r.id] ?? r.extraCharges);
    await this.act(() => this.api.resolveRental(r.id, charges, (this.notes()[r.id] ?? '').trim()), 'Settled. The money is shared out within a minute.');
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
      if (this.tab() === 'review') this.listings.set(await this.api.rentalListings('IN_REVIEW'));
      else this.bookings.set(await this.api.rentalBookings(this.tab() === 'disputes' ? 'disputes' : 'all'));
      this.error.set(null);
    } catch (e) {
      this.error.set(problemOf(e).detail);
    }
  }
}
