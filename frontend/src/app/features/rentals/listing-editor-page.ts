import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, effect, inject, input, signal, untracked } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { problemOf } from '../../core/errors';
import { DOCUMENT_LABELS, DocumentKind, FuelPolicy, fromLocalInput, ListingRequest, MyListing, RentalsApi } from '../../core/rentals-api';
import { RidesApi } from '../../core/rides-api';
import { Place, Vehicle } from '../../core/rides.models';
import { ToastService } from '../../core/toast.service';
import { shrinkPhoto } from '../../core/verification-api';
import { Icon } from '../../shared/icon';
import { PlaceInput } from '../../shared/place-input';

const STATUS: Record<string, { text: string; tone: string; help: string }> = {
  DRAFT: { text: 'Draft', tone: 'muted', help: 'Add photos and the documents, then send it for checking.' },
  IN_REVIEW: { text: 'Being checked', tone: 'warning', help: 'Our team checks the documents, usually within a day.' },
  LIVE: { text: 'Live', tone: 'success', help: 'Renters can find and book it.' },
  PAUSED: { text: 'Paused', tone: 'muted', help: 'Hidden from search until you resume it.' },
  REJECTED: { text: 'Needs changes', tone: 'danger', help: 'Fix what our team asked for, then send it again.' },
};

/** Listing a car for rent, or managing a listing: terms, photos, documents, blocked dates and status. */
@Component({
  selector: 'app-listing-editor-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [DatePipe, RouterLink, Icon, PlaceInput],
  template: `
    <div class="container-page max-w-3xl py-8 sm:py-10">
      <a routerLink="/rentals/mine" [queryParams]="{ tab: 'cars' }" class="btn btn-ghost btn-sm -ml-2"><app-icon name="arrowLeft" [size]="16" /> My cars</a>
      <h1 class="mt-2 text-3xl font-extrabold tracking-tight">{{ mine() ? mine()!.listing.car.name : 'List a car for rent' }}</h1>

      @if (mine(); as m) {
        <section class="card mt-5 p-5">
          <div class="flex flex-wrap items-center justify-between gap-3">
            <span><span class="badge" [class]="'tone-' + status(m.listing.status).tone">{{ status(m.listing.status).text }}</span>
              @if (m.listing.verified) { <span class="badge badge-success ml-1"><app-icon name="shield" [size]="12" /> Documents checked</span> }</span>
            <div class="flex flex-wrap gap-2">
              @if (m.listing.status === 'DRAFT' || m.listing.status === 'REJECTED') { <button type="button" class="btn btn-primary btn-sm" (click)="action('submit')" [disabled]="busy()">Send for checking</button> }
              @if (m.listing.status === 'LIVE') {
                <a [routerLink]="['/rentals', m.listing.id]" class="btn btn-secondary btn-sm">See it as renters do</a>
                <button type="button" class="btn btn-ghost btn-sm" (click)="action('pause')" [disabled]="busy()">Pause</button>
              }
              @if (m.listing.status === 'PAUSED') { <button type="button" class="btn btn-primary btn-sm" (click)="action('resume')" [disabled]="busy()">Resume</button> }
            </div>
          </div>
          <p class="mt-2 text-sm text-slate-600 dark:text-slate-400">{{ status(m.listing.status).help }}</p>
          @if (m.reviewNote) { <p class="mt-2 rounded-2xl bg-rose-50 p-3 text-sm text-rose-800 dark:bg-rose-500/10 dark:text-rose-200"><b>Our team:</b> {{ m.reviewNote }}</p> }
        </section>
      }

      <form class="card mt-5 space-y-4 p-5 sm:p-6" (submit)="save($event)" novalidate>
        <h2 class="text-lg font-bold">The car and your terms</h2>
        @if (!mine()) {
          <div>
            <label for="e-car" class="label">Which car?</label>
            @if (cars().length) {
              <select id="e-car" class="input" [value]="vehicleId()" (change)="vehicleId.set($any($event.target).value)">
                <option value="" disabled>Choose…</option>
                @for (c of cars(); track c.id) { <option [value]="c.id">{{ c.make }} {{ c.model }} · {{ c.plate }}</option> }
              </select>
            } @else {
              <p class="text-sm text-slate-600 dark:text-slate-400">Add your car first on <a routerLink="/profile" class="font-semibold underline">your profile</a> (My cars).</p>
            }
          </div>
        }
        <fieldset>
          <legend class="label">How it's rented</legend>
          <div class="flex flex-wrap gap-4 text-sm">
            <label class="flex items-center gap-2"><input type="checkbox" class="h-4 w-4 accent-brand-600" [checked]="selfDrive()" (change)="selfDrive.set($any($event.target).checked)" /> Self-drive (the renter drives)</label>
            <label class="flex items-center gap-2"><input type="checkbox" class="h-4 w-4 accent-brand-600" [checked]="withDriver()" (change)="withDriver.set($any($event.target).checked)" /> With driver (you drive)</label>
          </div>
        </fieldset>
        <div class="grid gap-3 sm:grid-cols-3">
          <label class="text-sm"><span class="label">Price per day (₹)</span><input class="input" type="number" min="100" [value]="dayPrice()" (input)="dayPrice.set($any($event.target).value)" /></label>
          <label class="text-sm"><span class="label">Per hour (₹, optional)</span><input class="input" type="number" min="50" [value]="hourPrice()" (input)="hourPrice.set($any($event.target).value)" placeholder="Leave empty for days only" /></label>
          @if (selfDrive()) { <label class="text-sm"><span class="label">Deposit (₹, refundable)</span><input class="input" type="number" min="0" [value]="deposit()" (input)="deposit.set($any($event.target).value)" /></label> }
          <label class="text-sm"><span class="label">Km included per day</span><input class="input" type="number" min="0" [value]="kmPerDay()" (input)="kmPerDay.set($any($event.target).value)" /></label>
          <label class="text-sm"><span class="label">Extra km (₹/km)</span><input class="input" type="number" min="0" [value]="extraKmPrice()" (input)="extraKmPrice.set($any($event.target).value)" /></label>
          <label class="text-sm"><span class="label">Fuel</span>
            <select class="input" [value]="fuelPolicy()" (change)="fuelPolicy.set($any($event.target).value)">
              <option value="SAME_LEVEL">Return with the same level</option><option value="INCLUDED">Included in the price</option>
            </select></label>
          @if (selfDrive()) { <label class="text-sm"><span class="label">Minimum age (self-drive)</span><input class="input" type="number" min="18" max="30" [value]="minAge()" (input)="minAge.set($any($event.target).value)" /></label> }
        </div>
        <div class="grid gap-3 sm:grid-cols-2">
          <div><label class="label" for="e-place">Town for pickup</label><app-place-input inputId="e-place" [(value)]="place" /></div>
          <label class="text-sm"><span class="label">Area (everyone sees this)</span><input class="input" maxlength="120" [value]="area()" (input)="area.set($any($event.target).value)" placeholder="e.g. Near MG Road metro" /></label>
        </div>
        <label class="block text-sm"><span class="label">Exact pickup address (only renters you accept see it)</span><input class="input" maxlength="300" [value]="pickupAddress()" (input)="pickupAddress.set($any($event.target).value)" /></label>
        <label class="block text-sm"><span class="label">About the car</span><textarea class="input min-h-20" maxlength="1000" [value]="description()" (input)="description.set($any($event.target).value)" placeholder="AC, music system, boot space, child seat…"></textarea></label>
        <label class="block text-sm"><span class="label">House rules</span><textarea class="input min-h-16" maxlength="500" [value]="rules()" (input)="rules.set($any($event.target).value)" placeholder="No smoking, no pets, inter-state trips by arrangement…"></textarea></label>
        <label class="flex items-center gap-2 text-sm"><input type="checkbox" class="h-4 w-4 accent-brand-600" [checked]="instant()" (change)="instant.set($any($event.target).checked)" /> Instant booking (no need to accept each request)</label>
        @if (formError()) { <p class="field-error" role="alert">{{ formError() }}</p> }
        <div class="flex justify-end"><button type="submit" class="btn btn-primary" [disabled]="busy()">{{ mine() ? 'Save changes' : 'Create listing' }}</button></div>
      </form>

      @if (mine(); as m) {
        <section class="card mt-5 p-5 sm:p-6" aria-labelledby="photos-heading">
          <h2 id="photos-heading" class="text-lg font-bold">Photos</h2>
          <p class="text-sm text-slate-600 dark:text-slate-400">Clear photos of the outside and inside. Up to 8.</p>
          <div class="mt-3 grid grid-cols-3 gap-2 sm:grid-cols-4">
            @for (p of m.photos; track p.id) {
              <span class="relative aspect-[4/3] overflow-hidden rounded-xl"><img [src]="p.url" alt="" class="h-full w-full object-cover" />
                <button type="button" class="absolute top-1 right-1 rounded-full bg-black/60 p-1 text-white" (click)="removePhoto(p.id)" aria-label="Remove photo"><app-icon name="trash" [size]="14" /></button></span>
            }
            @if (m.photos.length < 8) {
              <label class="flex aspect-[4/3] cursor-pointer flex-col items-center justify-center rounded-xl border-2 border-dashed border-slate-300 text-sm text-slate-500 dark:border-slate-600">
                <app-icon name="camera" [size]="22" /> Add photo<input type="file" accept="image/jpeg,image/png" class="sr-only" (change)="addPhoto($event)" /></label>
            }
          </div>
        </section>

        <section class="card mt-5 p-5 sm:p-6" aria-labelledby="docs-heading">
          <h2 id="docs-heading" class="text-lg font-bold">Documents</h2>
          <p class="text-sm text-slate-600 dark:text-slate-400">Only our checking team sees them; they're stored encrypted. Changing one means checking again.</p>
          <ul class="mt-3 divide-y divide-slate-100 dark:divide-slate-800">
            @for (k of docKinds; track k) {
              <li class="flex flex-wrap items-center gap-3 py-3">
                <span class="min-w-0 flex-1"><span class="block font-medium">{{ docLabels[k] }}</span>
                  @if (docOf(m, k); as d) { <span class="text-xs text-brand-700 dark:text-brand-400">Uploaded {{ d.uploadedAt | date: 'd MMM y' }}</span> } @else { <span class="text-xs text-slate-500">Not uploaded</span> }</span>
                <label class="btn btn-secondary btn-sm cursor-pointer">{{ docOf(m, k) ? 'Replace' : 'Upload' }}<input type="file" accept="image/jpeg,image/png" class="sr-only" (change)="addDocument(k, $event)" /></label>
              </li>
            }
          </ul>
        </section>

        <section class="card mt-5 p-5 sm:p-6" aria-labelledby="blocks-heading">
          <h2 id="blocks-heading" class="text-lg font-bold">When it isn't available</h2>
          @if (m.blocks.length) {
            <ul class="mt-2 space-y-2 text-sm">
              @for (b of m.blocks; track b.id) {
                <li class="flex items-center gap-2"><span class="flex-1">{{ b.from | date: 'd MMM, h:mm a' }} – {{ b.to | date: 'd MMM, h:mm a' }}@if (b.note) { · {{ b.note }} }</span>
                  <button type="button" class="btn btn-ghost btn-icon" (click)="removeBlock(b.id)" aria-label="Remove"><app-icon name="trash" [size]="16" /></button></li>
              }
            </ul>
          }
          <p class="mt-1 text-sm text-slate-600 dark:text-slate-400">Block dates when you need the car yourself. Renters won't see it then.</p>
          <div class="mt-3 grid gap-2 sm:grid-cols-2">
            <label class="min-w-0 text-sm"><span class="label text-xs">From</span><input type="datetime-local" class="input" [value]="blockFrom()" (input)="blockFrom.set($any($event.target).value)" /></label>
            <label class="min-w-0 text-sm"><span class="label text-xs">Until</span><input type="datetime-local" class="input" [value]="blockTo()" (input)="blockTo.set($any($event.target).value)" /></label>
          </div>
          <div class="mt-2 flex flex-wrap items-end gap-2">
            <label class="min-w-48 flex-1 text-sm"><span class="label text-xs">Note (optional)</span><input class="input" maxlength="100" [value]="blockNote()" (input)="blockNote.set($any($event.target).value)" placeholder="Family trip" /></label>
            <button type="button" class="btn btn-secondary" (click)="addBlock()" [disabled]="busy() || !blockFrom() || !blockTo()">Block these dates</button>
          </div>
        </section>

        <section class="mt-5 text-center">
          @if (confirmRemove()) {
            <div class="card p-4 text-sm"><p>Remove this listing? Renters won't see it any more.</p>
              <div class="mt-3 flex justify-center gap-2"><button type="button" class="btn btn-ghost btn-sm" (click)="confirmRemove.set(false)">Keep it</button>
                <button type="button" class="btn btn-sm bg-rose-600 text-white hover:bg-rose-700" (click)="remove()" [disabled]="busy()">Remove</button></div></div>
          } @else {
            <button type="button" class="btn btn-ghost btn-sm text-rose-600 dark:text-rose-400" (click)="confirmRemove.set(true)"><app-icon name="trash" [size]="16" /> Remove listing</button>
          }
        </section>
      }
    </div>
  `,
})
export class ListingEditorPage {
  private readonly api = inject(RentalsApi);
  private readonly rides = inject(RidesApi);
  private readonly router = inject(Router);
  private readonly toast = inject(ToastService);

  readonly id = input<string | undefined>(undefined);

  protected readonly docKinds = Object.keys(DOCUMENT_LABELS) as DocumentKind[];
  protected readonly docLabels = DOCUMENT_LABELS;
  protected readonly mine = signal<MyListing | null>(null);
  protected readonly cars = signal<Vehicle[]>([]);
  protected readonly busy = signal(false);
  protected readonly formError = signal<string | null>(null);
  protected readonly confirmRemove = signal(false);

  protected readonly vehicleId = signal('');
  protected readonly selfDrive = signal(true);
  protected readonly withDriver = signal(false);
  protected readonly dayPrice = signal('2000');
  protected readonly hourPrice = signal('');
  protected readonly deposit = signal('5000');
  protected readonly kmPerDay = signal('250');
  protected readonly extraKmPrice = signal('10');
  protected readonly fuelPolicy = signal<FuelPolicy>('SAME_LEVEL');
  protected readonly minAge = signal('21');
  protected readonly place = signal<Place | null>(null);
  protected readonly area = signal('');
  protected readonly pickupAddress = signal('');
  protected readonly description = signal('');
  protected readonly rules = signal('');
  protected readonly instant = signal(false);
  protected readonly blockFrom = signal('');
  protected readonly blockTo = signal('');
  protected readonly blockNote = signal('');

  constructor() {
    effect(() => {
      const id = this.id();
      untracked(() => void this.load(id));
    });
  }

  protected status(s: string) {
    return STATUS[s] ?? { text: s, tone: 'muted', help: '' };
  }

  protected docOf(m: MyListing, kind: DocumentKind) {
    return m.documents.find((d) => d.kind === kind) ?? null;
  }

  protected async save(event: Event): Promise<void> {
    event.preventDefault();
    const body = this.request();
    if (!body) return;
    this.busy.set(true);
    try {
      const m = this.mine();
      const saved = m ? await this.api.updateListing(m.listing.id, body) : await this.api.createListing(body);
      this.mine.set(saved);
      this.toast.success(m ? 'Saved.' : 'Created! Now add photos and the documents.');
      if (!m) void this.router.navigate(['/rentals/listings', saved.listing.id], { replaceUrl: true });
    } catch (e) {
      const p = problemOf(e);
      this.formError.set(p.errors ? Object.values(p.errors)[0] : p.detail);
    } finally {
      this.busy.set(false);
    }
  }

  protected async action(a: 'submit' | 'pause' | 'resume'): Promise<void> {
    const m = this.mine();
    if (!m) return;
    await this.run(() => this.api.listingAction(m.listing.id, a),
      a === 'submit' ? 'Sent for checking. We usually reply within a day.' : a === 'pause' ? 'Paused.' : 'Resumed.');
  }

  protected async addPhoto(event: Event): Promise<void> {
    const m = this.mine();
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    input.value = '';
    if (!m || !file) return;
    const small = await shrinkPhoto(file);
    await this.run(() => this.api.addPhoto(m.listing.id, small), 'Photo added.');
  }

  protected async removePhoto(photoId: string): Promise<void> {
    const m = this.mine();
    if (m) await this.run(() => this.api.removePhoto(m.listing.id, photoId), 'Photo removed.');
  }

  protected async addDocument(kind: DocumentKind, event: Event): Promise<void> {
    const m = this.mine();
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    input.value = '';
    if (!m || !file) return;
    const small = await shrinkPhoto(file);
    await this.run(() => this.api.addDocument(m.listing.id, kind, small), 'Document uploaded.');
  }

  protected async addBlock(): Promise<void> {
    const m = this.mine();
    const from = fromLocalInput(this.blockFrom());
    const to = fromLocalInput(this.blockTo());
    if (!m || !from || !to) return;
    if (await this.run(() => this.api.addBlock(m.listing.id, from, to, this.blockNote().trim() || null), 'Blocked.')) {
      this.blockFrom.set('');
      this.blockTo.set('');
      this.blockNote.set('');
    }
  }

  protected async removeBlock(blockId: string): Promise<void> {
    const m = this.mine();
    if (m) await this.run(() => this.api.removeBlock(m.listing.id, blockId), 'Unblocked.');
  }

  protected async remove(): Promise<void> {
    const m = this.mine();
    if (!m) return;
    this.busy.set(true);
    try {
      await this.api.removeListing(m.listing.id);
      this.toast.success('Listing removed.');
      void this.router.navigate(['/rentals/mine'], { queryParams: { tab: 'cars' } });
    } catch (e) {
      this.toast.error(problemOf(e).detail);
    } finally {
      this.busy.set(false);
    }
  }

  private request(): ListingRequest | null {
    const p = this.place();
    const vehicleId = this.mine()?.listing.car.vehicleId ?? this.vehicleId();
    const num = (v: string) => (v.trim() === '' ? NaN : Number(v));
    if (!vehicleId) return this.fail('Choose the car.');
    if (!this.selfDrive() && !this.withDriver()) return this.fail('Choose self-drive, with driver, or both.');
    if (!p) return this.fail('Choose the town for pickup.');
    if (!this.area().trim()) return this.fail('Say roughly where the pickup is.');
    const day = num(this.dayPrice());
    if (!(day >= 100)) return this.fail('Set a price per day of at least ₹100.');
    this.formError.set(null);
    return {
      vehicleId,
      selfDrive: this.selfDrive(),
      withDriver: this.withDriver(),
      dayPrice: day,
      hourPrice: Number.isNaN(num(this.hourPrice())) ? null : num(this.hourPrice()),
      deposit: Number.isNaN(num(this.deposit())) ? 0 : num(this.deposit()),
      kmPerDay: Number.isNaN(num(this.kmPerDay())) ? 0 : num(this.kmPerDay()),
      extraKmPrice: Number.isNaN(num(this.extraKmPrice())) ? 0 : num(this.extraKmPrice()),
      fuelPolicy: this.fuelPolicy(),
      minAge: Number.isNaN(num(this.minAge())) ? 21 : num(this.minAge()),
      placeId: p.id,
      area: this.area().trim(),
      pickupAddress: this.pickupAddress().trim() || null,
      description: this.description().trim() || null,
      rules: this.rules().trim() || null,
      instant: this.instant(),
    };
  }

  private fail(message: string): null {
    this.formError.set(message);
    return null;
  }

  private async run(call: () => Promise<MyListing>, done: string): Promise<boolean> {
    this.busy.set(true);
    try {
      this.mine.set(await call());
      this.toast.success(done);
      return true;
    } catch (e) {
      this.toast.error(problemOf(e).detail);
      return false;
    } finally {
      this.busy.set(false);
    }
  }

  private async load(id: string | undefined): Promise<void> {
    try {
      if (!id) {
        this.cars.set(await this.rides.myVehicles());
        return;
      }
      const m = await this.api.myListing(id);
      this.mine.set(m);
      const l = m.listing;
      this.selfDrive.set(l.selfDrive);
      this.withDriver.set(l.withDriver);
      this.dayPrice.set(String(l.dayPrice));
      this.hourPrice.set(l.hourPrice ? String(l.hourPrice) : '');
      this.deposit.set(String(l.deposit));
      this.kmPerDay.set(String(l.kmPerDay));
      this.extraKmPrice.set(String(l.extraKmPrice));
      this.fuelPolicy.set(l.fuelPolicy);
      this.minAge.set(String(l.minAge));
      this.area.set(l.area);
      this.pickupAddress.set(m.pickupAddress ?? '');
      this.description.set(l.description ?? '');
      this.rules.set(l.rules ?? '');
      this.instant.set(l.instant);
      this.place.set({ id: l.placeId, name: l.placeName, state: l.state ?? '', lat: l.lat, lng: l.lng, population: 0 });
    } catch (e) {
      this.toast.error(problemOf(e).detail);
    }
  }
}
