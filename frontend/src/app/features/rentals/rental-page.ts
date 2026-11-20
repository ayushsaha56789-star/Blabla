import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, effect, inject, input, signal, untracked } from '@angular/core';
import { RouterLink } from '@angular/router';
import { CheckoutService } from '../../core/checkout.service';
import { problemOf } from '../../core/errors';
import { paise, PaymentOptions, PaymentsApi, PaymentView, RefundPreview } from '../../core/payments-api';
import { FeaturesService } from '../../core/features.service';
import { chargesLine, CheckForm, completedLine, FUEL_LABELS, RentalsApi, rentalStatus, RentalView, toLocalInput } from '../../core/rentals-api';
import { rupees, whatsappLink } from '../../core/rides-format';
import { ToastService } from '../../core/toast.service';
import { AuthImage } from '../../shared/auth-image';
import { Avatar } from '../../shared/avatar';
import { Icon } from '../../shared/icon';
import { PhonePipe } from '../../shared/phone.pipe';
import { CheckFormComponent } from './check-form';

/** One rental, from the renter's or the owner's side: what happens next, and doing it. */
@Component({
  selector: 'app-rental-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [DatePipe, RouterLink, AuthImage, Avatar, Icon, PhonePipe, CheckFormComponent],
  template: `
    <div class="container-page max-w-3xl py-6 sm:py-10">
      <a routerLink="/rentals/mine" class="btn btn-ghost btn-sm -ml-2"><app-icon name="arrowLeft" [size]="16" /> My rentals</a>
      @if (error()) {
        <p class="card mt-4 p-6 text-center" role="alert">{{ error() }}</p>
      } @else if (rental(); as r) {
        <section class="card mt-3 overflow-hidden">
          <div class="flex gap-4 p-5">
            <div class="h-20 w-28 shrink-0 overflow-hidden rounded-2xl bg-slate-100 dark:bg-slate-800">
              @if (r.listing.photoUrl) { <img [src]="r.listing.photoUrl" [alt]="r.listing.car.name" class="h-full w-full object-cover" /> }
            </div>
            <div class="min-w-0 flex-1">
              <span class="badge" [class]="'tone-' + state().tone">{{ state().text }}</span>
              <h1 class="mt-1 truncate text-xl font-extrabold">{{ r.listing.car.name }}</h1>
              <p class="text-sm text-slate-600 dark:text-slate-400">{{ r.mode === 'SELF_DRIVE' ? 'Self-drive' : 'With driver' }} · {{ r.listing.area }}, {{ r.listing.placeName }}</p>
            </div>
          </div>
          <dl class="grid grid-cols-2 gap-px border-t border-slate-100 bg-slate-100 text-sm dark:border-slate-800 dark:bg-slate-800">
            <div class="bg-white p-4 dark:bg-slate-900"><dt class="text-slate-500 dark:text-slate-400">Pickup</dt><dd class="font-semibold">{{ r.from | date: 'EEE d MMM, h:mm a' }}</dd></div>
            <div class="bg-white p-4 dark:bg-slate-900"><dt class="text-slate-500 dark:text-slate-400">Return</dt><dd class="font-semibold">{{ r.to | date: 'EEE d MMM, h:mm a' }}</dd></div>
            <div class="bg-white p-4 dark:bg-slate-900"><dt class="text-slate-500 dark:text-slate-400">Rent</dt><dd class="font-semibold">{{ money(r.rent) }} · {{ r.kmIncluded }} km included</dd></div>
            <div class="bg-white p-4 dark:bg-slate-900"><dt class="text-slate-500 dark:text-slate-400">Deposit</dt><dd class="font-semibold">{{ r.deposit ? money(r.deposit) + ' (refundable)' : 'None' }}</dd></div>
          </dl>
        </section>

        @if (r.viewer === 'RENTER' && r.mode === 'SELF_DRIVE' && (r.status === 'ACCEPTED' || r.status === 'PAID') && features.isOn('drivers.hire')) {
          <a routerLink="/drivers" [queryParams]="driverSearch()" class="card mt-4 flex items-center gap-3 p-4 transition hover:shadow-md">
            <span class="flex h-10 w-10 shrink-0 items-center justify-center rounded-2xl bg-brand-50 text-brand-700 dark:bg-brand-500/15 dark:text-brand-300"><app-icon name="steering" [size]="20" /></span>
            <span class="min-w-0 flex-1 text-sm"><span class="block font-semibold">Rather not drive?</span>
              <span class="block text-slate-600 dark:text-slate-400">Hire a checked driver for this rental.</span></span>
            <app-icon name="arrowRight" [size]="18" class="text-slate-400" />
          </a>
        }

        <!-- Who, and how to reach them -->
        <section class="card mt-4 p-5">
          @if (r.viewer === 'RENTER') {
            <p class="kicker">Owner</p>
            <div class="mt-2 flex items-center gap-3"><app-avatar [url]="r.owner.avatarUrl" [name]="r.owner.displayName" [size]="40" /><span class="font-semibold">{{ r.owner.displayName }}</span></div>
          } @else {
            <p class="kicker">Renter</p>
            <div class="mt-2 flex items-center gap-3"><app-avatar [url]="r.renter.avatarUrl" [name]="r.renter.displayName" [size]="40" />
              <span><span class="block font-semibold">{{ r.renter.displayName }}</span>@if (r.renter.idVerified) { <span class="text-xs text-brand-700 dark:text-brand-400">ID verified</span> }</span></div>
            @if (r.message) { <p class="mt-3 rounded-2xl bg-slate-50 p-3 text-sm dark:bg-slate-800/60">“{{ r.message }}”</p> }
          }
          @if (r.otherPhone) {
            <div class="mt-4 grid grid-cols-2 gap-2">
              <a class="btn btn-secondary" [href]="'tel:' + r.otherPhone"><app-icon name="phone" [size]="18" /> Call</a>
              <a class="btn btn-secondary" [href]="wa(r.otherPhone)" target="_blank" rel="noopener noreferrer"><app-icon name="chat" [size]="18" /> WhatsApp</a>
            </div>
            <p class="hint">{{ r.otherPhone | phone }}</p>
          }
          @if (r.pickupAddress) { <p class="mt-3 text-sm"><b>Pickup:</b> {{ r.pickupAddress }}</p> }
        </section>

        <!-- Money -->
        <section class="card mt-4 p-5 text-sm">
          <p class="kicker">Money</p>
          @if (!r.payOnline) {
            @if (r.pickedUpAt || r.status === 'REQUESTED' || r.status === 'ACCEPTED') {
              <p class="mt-2">{{ r.pickedUpAt ? 'Paid to the owner at pickup' : 'Pay the owner at pickup' }}: <b>{{ money(r.rent + r.deposit) }}</b>
                (rent {{ money(r.rent) }}{{ r.deposit ? ' + refundable deposit ' + money(r.deposit) : '' }}).</p>
            } @else { <p class="mt-2">Nothing to pay.</p> }
          } @else if (payment(); as pay) {
            <p class="mt-2">@if (pay.status === 'PAID') { Paid online: <b>{{ cash(pay.onlinePaise + pay.creditsPaise) }}</b>. } @else { Waiting for payment. }</p>
            @for (rf of pay.refunds; track rf.id) {
              <p class="mt-1 text-brand-700 dark:text-brand-400">Refund {{ cash(rf.onlinePaise + rf.creditsPaise) }}: {{ rf.status === 'PROCESSED' ? 'sent' : rf.status === 'FAILED' ? 'needs a retry (we are on it)' : 'on its way' }}.</p>
            }
          } @else {
            <p class="mt-2">Paid online once the owner accepts: <b>{{ money(r.rent + r.deposit) }}</b>. The deposit comes back after the return.</p>
          }
          @if (r.status === 'ACCEPTED' && r.payOnline && r.viewer === 'RENTER') {
            @if (options()?.credits && options()!.creditsPaise > 0) {
              <label class="mt-3 flex items-center gap-2"><input type="checkbox" class="h-4 w-4 accent-brand-600" [checked]="useCredits()" (change)="useCredits.set($any($event.target).checked)" /> Use my credits ({{ cash(options()!.creditsPaise) }})</label>
            }
            <button type="button" class="btn btn-primary mt-3 w-full" (click)="pay()" [disabled]="busy()"><app-icon name="rupee" [size]="18" /> Pay {{ money(r.rent + r.deposit) }}</button>
            @if (r.payBy) { <p class="hint text-center">Pay by {{ r.payBy | date: 'd MMM, h:mm a' }}, or the booking lapses.</p> }
          }
        </section>

        <!-- What's next -->
        <section class="card mt-4 p-5" aria-labelledby="next-heading">
          <h2 id="next-heading" class="kicker">What's next</h2>
          @switch (r.status) {
            @case ('REQUESTED') {
              @if (r.viewer === 'OWNER') {
                <p class="mt-2 text-sm">{{ r.renter.displayName }} wants your car. Accept to confirm it for these dates.</p>
                <div class="mt-3 grid grid-cols-2 gap-2">
                  <button type="button" class="btn btn-secondary" (click)="act('decline', 'Declined.')" [disabled]="busy()">Decline</button>
                  <button type="button" class="btn btn-primary" (click)="act('accept', 'Accepted! ' + (r.payOnline ? 'The renter pays next.' : 'Agree the pickup time with the renter.'))" [disabled]="busy()">Accept</button>
                </div>
              } @else { <p class="mt-2 text-sm">The owner will accept or decline within a day.</p> }
            }
            @case ('ACCEPTED') {
              <p class="mt-2 text-sm">
                @if (r.payOnline && r.viewer === 'RENTER') { Pay to confirm (above). }
                @else if (r.payOnline) { Waiting for the renter to pay. }
                @else { At pickup the owner records the car (odometer, fuel, photos) and the renter pays {{ money(r.rent + r.deposit) }}. }
              </p>
            }
            @case ('PAID') { <p class="mt-2 text-sm">Paid. At pickup the owner records the car's odometer, fuel and photos.</p> }
            @case ('PICKED_UP') { <p class="mt-2 text-sm">Have a good trip! At the return the owner records the car again.</p> }
            @case ('RETURNED') {
              @if (r.viewer === 'RENTER') {
                <p class="mt-2 text-sm">
                  @if (r.kmDriven !== null) { You drove {{ r.kmDriven }} km ({{ r.kmIncluded }} included). }
                  {{ chargesLine(r) }}
                </p>
                @if (disputing()) {
                  <div class="mt-3 space-y-2">
                    <textarea class="input min-h-20 text-sm" maxlength="500" [value]="disputeNote()" (input)="disputeNote.set($any($event.target).value)" placeholder="What's wrong with the charges?"></textarea>
                    <div class="flex justify-end gap-2"><button type="button" class="btn btn-ghost btn-sm" (click)="disputing.set(false)">Cancel</button>
                      <button type="button" class="btn btn-primary btn-sm" (click)="dispute()" [disabled]="busy() || disputeNote().trim().length < 5">Ask SaathChalo to check</button></div>
                  </div>
                } @else {
                  <div class="mt-3 grid grid-cols-2 gap-2">
                    <button type="button" class="btn btn-secondary" (click)="disputing.set(true)">Question the charges</button>
                    <button type="button" class="btn btn-primary" (click)="act('accept-return', 'Thanks! The rental is complete.')" [disabled]="busy()">Accept and finish</button>
                  </div>
                  @if (r.respondBy) { <p class="hint text-center">Finishes by itself on {{ r.respondBy | date: 'd MMM, h:mm a' }}.</p> }
                }
              } @else { <p class="mt-2 text-sm">Waiting for the renter to accept the charges (it finishes by itself after 48 hours).</p> }
            }
            @case ('DISPUTED') { <p class="mt-2 text-sm">SaathChalo is checking the charges and will settle them here.@if (r.disputeNote) { The renter said: “{{ r.disputeNote }}” }</p> }
            @case ('COMPLETED') {
              <p class="mt-2 text-sm">{{ completedLine(r) }}</p>
              @if (r.resolution) { <p class="mt-2 rounded-2xl bg-slate-50 p-3 text-sm dark:bg-slate-800/60"><b>SaathChalo decided:</b> {{ r.resolution }}</p> }
            }
            @default { <p class="mt-2 text-sm">This booking is closed.</p> }
          }

          @if (canCancel()) {
            @if (askCancel()) {
              <div class="mt-4 rounded-2xl border border-rose-200 p-4 text-sm dark:border-rose-500/30">
                <p class="font-semibold">Cancel this booking?</p>
                @if (preview()?.paid) { <p class="mt-1">You get <b>{{ cash(preview()!.backOnlinePaise + preview()!.backCreditsPaise) }}</b> back. {{ preview()!.explanation }}</p> }
                <div class="mt-3 flex gap-2"><button type="button" class="btn btn-ghost flex-1" (click)="askCancel.set(false)">Keep it</button>
                  <button type="button" class="btn flex-1 bg-rose-600 text-white hover:bg-rose-700" (click)="act('cancel', 'Cancelled.')" [disabled]="busy()">Yes, cancel</button></div>
              </div>
            } @else {
              <button type="button" class="btn btn-ghost mt-4 w-full text-rose-600 dark:text-rose-400" (click)="openCancel()">Cancel booking</button>
            }
          }

          @if (canPickup()) {
            @if (form() === 'pickup') {
              <app-check-form class="mt-4" title="Hand over the car" [busy]="busy()" (saved)="check('pickup', $event)" (cancelled)="form.set(null)" />
            } @else {
              <button type="button" class="btn btn-primary mt-4 w-full" (click)="form.set('pickup')"><app-icon name="camera" [size]="18" /> Record the handover</button>
            }
          }
          @if (canRenterPhotos()) {
            @if (form() === 'renter') {
              <app-check-form class="mt-4" title="Your own photos of the car" [busy]="busy()" (saved)="check('pickup', $event)" (cancelled)="form.set(null)" />
            } @else {
              <button type="button" class="btn btn-secondary mt-4 w-full" (click)="form.set('renter')"><app-icon name="camera" [size]="18" /> Add your own photos (recommended)</button>
            }
          }
          @if (canReturn()) {
            @if (form() === 'return') {
              <app-check-form class="mt-4" title="Take the car back" [returning]="true" [startOdometer]="startOdometer()" [kmIncluded]="r.kmIncluded"
                              [extraKmPrice]="r.extraKmPrice" [busy]="busy()" (saved)="check('return', $event)" (cancelled)="form.set(null)" />
            } @else {
              <button type="button" class="btn btn-primary mt-4 w-full" (click)="form.set('return')"><app-icon name="camera" [size]="18" /> Record the return</button>
            }
          }
        </section>

        @if (r.checks.length) {
          <section class="mt-4" aria-labelledby="checks-heading">
            <h2 id="checks-heading" class="kicker mb-2">The car at pickup and return</h2>
            <div class="space-y-3">
              @for (c of r.checks; track $index) {
                <div class="card p-4 text-sm">
                  <p class="font-semibold">{{ c.stage === 'PICKUP' ? 'Pickup' : 'Return' }} · by the {{ c.byRole === 'OWNER' ? 'owner' : 'renter' }}
                    <span class="font-normal text-slate-500 dark:text-slate-400">· {{ c.at | date: 'd MMM, h:mm a' }}</span></p>
                  <p class="mt-1">{{ c.odometer }} km · fuel {{ fuelLabels[c.fuel] }}@if (c.notes) { · {{ c.notes }} }</p>
                  @if (c.photoUrls.length) {
                    <div class="mt-2 grid grid-cols-3 gap-2 sm:grid-cols-6">
                      @for (u of c.photoUrls; track u) { <app-auth-img [src]="u" alt="Photo of the car" class="h-20 w-full" /> }
                    </div>
                  }
                </div>
              }
            </div>
          </section>
        }
      } @else {
        <div class="skeleton mt-4 h-60"></div>
      }
    </div>
  `,
})
export class RentalPage {
  private readonly api = inject(RentalsApi);
  private readonly payments = inject(PaymentsApi);
  private readonly checkout = inject(CheckoutService);
  private readonly toast = inject(ToastService);

  readonly id = input.required<string>();

  protected readonly rental = signal<RentalView | null>(null);
  protected readonly payment = signal<PaymentView | null>(null);
  protected readonly options = signal<PaymentOptions | null>(null);
  protected readonly preview = signal<RefundPreview | null>(null);
  protected readonly error = signal<string | null>(null);
  protected readonly busy = signal(false);
  protected readonly form = signal<'pickup' | 'renter' | 'return' | null>(null);
  protected readonly askCancel = signal(false);
  protected readonly disputing = signal(false);
  protected readonly disputeNote = signal('');
  protected readonly useCredits = signal(true);
  protected readonly money = rupees;
  protected readonly cash = paise;
  protected readonly chargesLine = chargesLine;
  protected readonly completedLine = completedLine;
  protected readonly features = inject(FeaturesService);

  /** The driver search, filled in with this rental's town, start and length. */
  protected driverSearch(): Record<string, string | number> {
    const r = this.rental()!;
    const hours = Math.round((Date.parse(r.to) - Date.parse(r.from)) / 3600_000);
    return hours > 12
      ? { placeId: r.listing.placeId, from: toLocalInput(new Date(r.from)), kind: 'OUTSTATION', days: Math.min(15, Math.ceil(hours / 24)) }
      : { placeId: r.listing.placeId, from: toLocalInput(new Date(r.from)), kind: 'LOCAL', hours: Math.max(2, hours) };
  }
  protected readonly wa = whatsappLink;
  protected readonly fuelLabels = FUEL_LABELS;

  protected readonly state = computed(() => rentalStatus(this.rental()!));
  protected readonly canCancel = computed(() => {
    const r = this.rental();
    return !!r && r.viewer !== 'STAFF' && ['REQUESTED', 'ACCEPTED', 'PAID'].includes(r.status) && Date.parse(r.from) > Date.now();
  });
  protected readonly canPickup = computed(() => {
    const r = this.rental();
    return !!r && r.viewer === 'OWNER' && (r.payOnline ? r.status === 'PAID' : r.status === 'ACCEPTED')
      && Date.now() >= Date.parse(r.from) - 3 * 3600_000;
  });
  protected readonly canRenterPhotos = computed(() => {
    const r = this.rental();
    return !!r && r.viewer === 'RENTER' && r.status === 'PICKED_UP' && !r.checks.some((c) => c.byRole === 'RENTER' && c.stage === 'PICKUP');
  });
  protected readonly canReturn = computed(() => {
    const r = this.rental();
    return !!r && r.viewer === 'OWNER' && r.status === 'PICKED_UP';
  });
  protected readonly startOdometer = computed(() => this.rental()?.checks.find((c) => c.stage === 'PICKUP' && c.byRole === 'OWNER')?.odometer ?? null);

  constructor() {
    effect(() => {
      const id = this.id();
      untracked(() => void this.load(id));
    });
  }

  protected async act(action: 'accept' | 'decline' | 'cancel' | 'accept-return', done: string): Promise<void> {
    await this.run(() => this.api.bookingAction(this.id(), action), done);
    this.askCancel.set(false);
  }

  protected async openCancel(): Promise<void> {
    this.askCancel.set(true);
    this.preview.set(null);
    if (this.payment()?.status === 'PAID') {
      try {
        this.preview.set(await this.payments.refundPreview(this.id()));
      } catch {
        // The question still works.
      }
    }
  }

  protected async dispute(): Promise<void> {
    await this.run(() => this.api.dispute(this.id(), this.disputeNote().trim()), "Thanks. SaathChalo will check and settle it here.");
    this.disputing.set(false);
  }

  protected async check(stage: 'pickup' | 'return', f: CheckForm): Promise<void> {
    const ok = await this.run(() => this.api.check(this.id(), stage, f), stage === 'pickup' ? 'Recorded. Have a good trip!' : 'Return recorded.');
    if (ok) this.form.set(null);
  }

  protected async pay(): Promise<void> {
    const r = this.rental();
    if (!r) return;
    this.busy.set(true);
    try {
      const view = await this.api.pay(r.id, this.useCredits() && !!this.options()?.credits);
      if (view.status !== 'PAID') {
        const answer = await this.checkout.pay(view);
        if (!answer) {
          this.toast.info('Payment not finished. You can pay again from this page.');
          return;
        }
        await this.payments.confirm(view.paymentId, answer);
      }
      this.toast.success('Paid! The car is yours for these dates.');
    } catch (e) {
      this.toast.error(e instanceof Error && !('status' in e) ? e.message : problemOf(e).detail);
    } finally {
      this.busy.set(false);
      await this.load(r.id);
    }
  }

  private async run(call: () => Promise<RentalView>, done: string): Promise<boolean> {
    this.busy.set(true);
    try {
      this.rental.set(await call());
      this.toast.success(done);
      await this.loadPayment();
      return true;
    } catch (e) {
      const p = problemOf(e);
      this.toast.error(p.errors ? Object.values(p.errors)[0] : p.detail);
      return false;
    } finally {
      this.busy.set(false);
    }
  }

  private async load(id: string): Promise<void> {
    try {
      this.rental.set(await this.api.rental(id));
      await this.loadPayment();
    } catch (e) {
      this.error.set(problemOf(e).detail);
    }
  }

  private async loadPayment(): Promise<void> {
    const r = this.rental();
    if (!r?.payOnline) return;
    try {
      this.payment.set((await this.payments.forBookings([r.id]))[0] ?? null);
      if (r.viewer === 'RENTER' && !this.options()) this.options.set(await this.payments.options());
    } catch {
      // The rest of the page still works.
    }
  }
}
