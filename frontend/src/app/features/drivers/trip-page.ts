import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, DestroyRef, effect, inject, input, signal, untracked } from '@angular/core';
import { RouterLink } from '@angular/router';
import { CheckoutService } from '../../core/checkout.service';
import { DriversApi, tripLength, tripStatus, TripView } from '../../core/drivers-api';
import { problemOf } from '../../core/errors';
import { FeaturesService } from '../../core/features.service';
import { paise, PaymentOptions, PaymentsApi, PaymentView, RefundPreview } from '../../core/payments-api';
import { rupees, whatsappLink } from '../../core/rides-format';
import { ToastService } from '../../core/toast.service';
import { Avatar } from '../../shared/avatar';
import { Icon } from '../../shared/icon';
import { PhonePipe } from '../../shared/phone.pipe';
import { SosPanel } from '../../shared/sos-panel';

/** One hired-driver trip, from either side: what's agreed, what's next, the start code, money, SOS and ratings. */
@Component({
  selector: 'app-trip-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [DatePipe, RouterLink, Avatar, Icon, PhonePipe, SosPanel],
  template: `
    <div class="container-page max-w-2xl py-6 sm:py-10">
      <a routerLink="/drivers/trips" class="btn btn-ghost btn-sm -ml-2"><app-icon name="arrowLeft" [size]="16" /> Driver trips</a>
      @if (error()) {
        <p class="card mt-4 p-6 text-center" role="alert">{{ error() }}</p>
      } @else if (trip(); as t) {
        <section class="card mt-3 overflow-hidden">
          <div class="flex items-center gap-4 p-5">
            <app-avatar [url]="other().avatarUrl" [name]="other().displayName" [size]="64" />
            <div class="min-w-0 flex-1">
              <span class="badge" [class]="'tone-' + state().tone">{{ state().text }}</span>
              <h1 class="mt-1 truncate text-xl font-extrabold">{{ t.viewer === 'DRIVER' ? 'Driving for ' : 'Your driver: ' }}{{ other().displayName }}</h1>
              <p class="text-sm text-slate-600 dark:text-slate-400">
                @if (t.viewer === 'DRIVER') { @if (t.customerRating) { ★ {{ t.customerRating }} from drivers } @else { New to hiring drivers } }
                @else { @if (t.driverRating) { ★ {{ t.driverRating }} ({{ t.driverReviews }}) } @else { No ratings yet } }
              </p>
            </div>
          </div>
          <dl class="grid grid-cols-2 border-t border-slate-100 text-sm dark:border-slate-800">
            <div class="border-r border-b border-slate-100 p-4 dark:border-slate-800"><dt class="text-slate-500 dark:text-slate-400">From</dt><dd class="font-semibold">{{ t.from | date: 'EEE d MMM, h:mm a' }}</dd></div>
            <div class="border-b border-slate-100 p-4 dark:border-slate-800"><dt class="text-slate-500 dark:text-slate-400">Until</dt><dd class="font-semibold">{{ t.to | date: 'EEE d MMM, h:mm a' }}</dd></div>
            <div class="border-r border-slate-100 p-4 dark:border-slate-800"><dt class="text-slate-500 dark:text-slate-400">Trip</dt><dd class="font-semibold">{{ t.kind === 'LOCAL' ? 'In ' + t.placeName : t.placeName + ' → ' + t.destinationName }} · {{ length() }}</dd></div>
            <div class="p-4"><dt class="text-slate-500 dark:text-slate-400">Car</dt><dd class="font-semibold">{{ t.car }} · {{ t.automatic ? 'automatic' : 'manual' }}</dd></div>
          </dl>
        </section>

        @if (t.startCode) {
          <section class="card mt-4 p-5 text-center" aria-labelledby="code-heading">
            <h2 id="code-heading" class="kicker">Your start code</h2>
            <p class="mt-2 font-mono text-4xl font-extrabold tracking-[0.4em] text-brand-700 dark:text-brand-300" aria-live="polite">{{ t.startCode }}</p>
            <p class="mt-2 text-sm text-slate-600 dark:text-slate-400">Tell it to {{ t.driver.displayName }} when they arrive, not before. It proves the right driver came.</p>
            @if (canStart()) {
              <button type="button" class="btn btn-secondary btn-sm mt-3" (click)="start(null)" [disabled]="busy()">The driver is here: start the trip</button>
            }
          </section>
        }

        @if (t.otherPhone || t.pickupAddress) {
          <section class="card mt-4 p-5">
            <p class="kicker">{{ t.viewer === 'DRIVER' ? 'Member' : 'Driver' }}</p>
            @if (t.otherPhone) {
              <div class="mt-3 grid grid-cols-2 gap-2">
                <a class="btn btn-secondary" [href]="'tel:' + t.otherPhone"><app-icon name="phone" [size]="18" /> Call</a>
                <a class="btn btn-secondary" [href]="wa(t.otherPhone)" target="_blank" rel="noopener noreferrer"><app-icon name="chat" [size]="18" /> WhatsApp</a>
              </div>
              <p class="hint">{{ t.otherPhone | phone }}</p>
            }
            @if (t.pickupAddress) { <p class="mt-3 text-sm"><b>Pickup:</b> {{ t.pickupAddress }}, {{ t.placeName }}</p> }
            @if (t.message) { <p class="mt-2 text-sm text-slate-600 dark:text-slate-400"><b>Note:</b> {{ t.message }}</p> }
          </section>
        }

        <section class="card mt-4 p-5 text-sm">
          <p class="kicker">Money</p>
          <p class="mt-2">Fare for {{ length() }}: <b>{{ money(t.fare) }}</b>.
            @if (t.payOnline) {
              @if (payment(); as pay) { {{ pay.status === 'PAID' ? 'Paid online.' : 'Waiting for payment.' }} }
              @else { Paid online once the driver accepts. }
            } @else { Paid to the driver directly (cash or UPI) after the trip. }
          </p>
          @for (rf of payment()?.refunds ?? []; track rf.id) {
            <p class="mt-1 text-brand-700 dark:text-brand-400">Refund {{ cash(rf.onlinePaise + rf.creditsPaise) }}: {{ rf.status === 'PROCESSED' ? 'sent' : rf.status === 'FAILED' ? 'needs a retry (we are on it)' : 'on its way' }}.</p>
          }
          <p class="mt-1 text-slate-500 dark:text-slate-400">Extra time: {{ money(t.extraPrice) }} per {{ t.kind === 'LOCAL' ? 'hour' : 'day' }}, paid to the driver directly. Fuel, tolls and parking are the car owner's.</p>
          @if (t.extraUnits) {
            <p class="mt-2 rounded-2xl bg-accent-100/60 p-3 dark:bg-accent-600/10">The trip ran {{ t.extraUnits }} {{ t.kind === 'LOCAL' ? (t.extraUnits === 1 ? 'hour' : 'hours') : (t.extraUnits === 1 ? 'day' : 'days') }} over: <b>{{ money(t.extraUnits * t.extraPrice) }}</b> extra, paid to the driver directly.</p>
          }
          @if (t.status === 'ACCEPTED' && t.payOnline && t.viewer === 'CUSTOMER') {
            @if (options()?.credits && options()!.creditsPaise > 0) {
              <label class="mt-3 flex items-center gap-2"><input type="checkbox" class="h-4 w-4 accent-brand-600" [checked]="useCredits()" (change)="useCredits.set($any($event.target).checked)" /> Use my credits ({{ cash(options()!.creditsPaise) }})</label>
            }
            <button type="button" class="btn btn-primary mt-3 w-full" (click)="pay()" [disabled]="busy()"><app-icon name="rupee" [size]="18" /> Pay {{ money(t.fare) }}</button>
            @if (t.payBy) { <p class="hint text-center">Pay by {{ t.payBy | date: 'd MMM, h:mm a' }}, or the booking lapses.</p> }
          }
        </section>

        <section class="card mt-4 p-5" aria-labelledby="next-heading">
          <h2 id="next-heading" class="kicker">What's next</h2>
          @switch (t.status) {
            @case ('REQUESTED') {
              @if (t.viewer === 'DRIVER') {
                <p class="mt-2 text-sm">{{ t.customer.displayName }} would like you to drive their car. Answer by {{ t.answerBy | date: 'h:mm a, d MMM' }}.</p>
                <div class="mt-3 grid grid-cols-2 gap-2">
                  <button type="button" class="btn btn-secondary" (click)="act('decline', 'Declined.')" [disabled]="busy()">Decline</button>
                  <button type="button" class="btn btn-primary" (click)="act('accept', t.payOnline ? 'Accepted! The member pays next.' : 'Accepted! Call the member to agree the details.')" [disabled]="busy()">Accept</button>
                </div>
              } @else { <p class="mt-2 text-sm">{{ t.driver.displayName }} answers by {{ t.answerBy | date: 'h:mm a, d MMM' }}. If they can't, you can pick another driver.</p> }
            }
            @case ('ACCEPTED') {
              <p class="mt-2 text-sm">
                @if (t.payOnline && t.viewer === 'CUSTOMER') { Pay to confirm (above). }
                @else if (t.payOnline) { Waiting for the member to pay. }
                @else if (t.viewer === 'DRIVER') { Confirmed. Tap "On my way" when you set off. }
                @else { Confirmed. {{ t.driver.displayName }} will set off in time to reach you. }
              </p>
              @if (canSetOff()) { <button type="button" class="btn btn-primary mt-3 w-full" (click)="act('on-the-way', 'The member can see you\\'re on the way.')" [disabled]="busy()"><app-icon name="route" [size]="18" /> On my way</button> }
            }
            @case ('PAID') {
              <p class="mt-2 text-sm">Paid and confirmed. @if (t.viewer === 'DRIVER') { Tap "On my way" when you set off. }</p>
              @if (canSetOff()) { <button type="button" class="btn btn-primary mt-3 w-full" (click)="act('on-the-way', 'The member can see you\\'re on the way.')" [disabled]="busy()"><app-icon name="route" [size]="18" /> On my way</button> }
            }
            @case ('ON_THE_WAY') {
              <p class="mt-2 text-sm">@if (t.viewer === 'DRIVER') { When you meet, ask {{ t.customer.displayName }} for the 4-digit start code. } @else { {{ t.driver.displayName }} is on the way. Give them your start code when they arrive. }</p>
            }
            @case ('STARTED') {
              <p class="mt-2 text-sm">Trip in progress since {{ t.startedAt | date: 'h:mm a' }}. Booked until {{ t.to | date: 'h:mm a, d MMM' }}.</p>
              <button type="button" class="btn btn-primary mt-3 w-full" (click)="act('end', 'Trip finished. Thanks!')" [disabled]="busy()"><app-icon name="checkCircle" [size]="18" /> End the trip</button>
            }
            @case ('COMPLETED') {
              <p class="mt-2 text-sm">Finished {{ t.endedAt | date: 'h:mm a, d MMM' }}.</p>
              @if (t.canReview) {
                <div class="mt-3 rounded-2xl bg-slate-50 p-4 dark:bg-slate-800/60">
                  <p class="font-semibold">How was {{ other().displayName }}?</p>
                  <div class="mt-2 flex gap-1" role="radiogroup" aria-label="Stars">
                    @for (s of [1, 2, 3, 4, 5]; track s) {
                      <button type="button" role="radio" class="text-3xl leading-none" [class]="s <= stars() ? 'text-amber-500' : 'text-slate-300 dark:text-slate-600'" [attr.aria-checked]="s === stars()" [attr.aria-label]="s + (s === 1 ? ' star' : ' stars')" (click)="stars.set(s)">★</button>
                    }
                  </div>
                  <textarea class="input mt-3 min-h-16 text-sm" maxlength="500" [value]="comment()" (input)="comment.set($any($event.target).value)" placeholder="A few words (optional)"></textarea>
                  <button type="button" class="btn btn-primary btn-sm mt-3" (click)="rate()" [disabled]="busy() || !stars()">Send rating</button>
                </div>
              } @else if (t.myStars) { <p class="mt-2 text-sm text-slate-600 dark:text-slate-400">You rated this trip {{ t.myStars }} {{ t.myStars === 1 ? 'star' : 'stars' }}.</p> }
            }
            @case ('CANCELLED') { <p class="mt-2 text-sm">This trip was cancelled.@if (t.cancelReason) { “{{ t.cancelReason }}” }</p> }
            @default { <p class="mt-2 text-sm">This request is closed.@if (t.viewer === 'CUSTOMER' && features.isOn('drivers.hire')) { <a routerLink="/drivers" class="font-semibold text-brand-700 underline dark:text-brand-400">Find another driver</a> }</p> }
          }

          @if (t.viewer === 'DRIVER' && canStart()) {
            <form class="mt-4 flex items-end gap-2" (submit)="$event.preventDefault(); start(code())">
              <label class="flex-1 text-sm"><span class="label text-xs">Start code from the member</span>
                <input class="input text-center font-mono text-lg tracking-[0.3em]" inputmode="numeric" maxlength="4" autocomplete="off" [value]="code()" (input)="code.set($any($event.target).value)" placeholder="••••" /></label>
              <button type="submit" class="btn btn-primary" [disabled]="busy() || code().length !== 4">Start trip</button>
            </form>
            @if (t.wrongCodes) { <p class="hint">{{ t.wrongCodes }} wrong {{ t.wrongCodes === 1 ? 'try' : 'tries' }}. After 5, the member starts the trip on their phone.</p> }
          }

          @if (canCancel()) {
            @if (askCancel()) {
              <div class="mt-4 rounded-2xl border border-rose-200 p-4 text-sm dark:border-rose-500/30">
                <p class="font-semibold">{{ t.viewer === 'DRIVER' ? 'Cancel this trip? The member is counting on you.' : 'Cancel this trip?' }}</p>
                @if (preview()?.paid) { <p class="mt-1">You get <b>{{ cash(preview()!.backOnlinePaise + preview()!.backCreditsPaise) }}</b> back. {{ preview()!.explanation }}</p> }
                @if (t.viewer === 'DRIVER') {
                  <input class="input mt-2 text-sm" maxlength="300" [value]="reason()" (input)="reason.set($any($event.target).value)" placeholder="Why can't you come? The member will see this" />
                }
                <div class="mt-3 flex gap-2"><button type="button" class="btn btn-ghost flex-1" (click)="askCancel.set(false)">Keep it</button>
                  <button type="button" class="btn flex-1 bg-rose-600 text-white hover:bg-rose-700" (click)="cancel()" [disabled]="busy() || (t.viewer === 'DRIVER' && reason().trim().length < 3)">Yes, cancel</button></div>
              </div>
            } @else {
              <button type="button" class="btn btn-ghost mt-4 w-full text-rose-600 dark:text-rose-400" (click)="openCancel()">Cancel trip</button>
            }
          }
        </section>

        @if (features.isOn('safety.sos') && (t.status === 'ON_THE_WAY' || t.status === 'STARTED')) {
          <section class="card mt-4 p-5" aria-label="Safety"><app-sos-panel /></section>
        }
      } @else {
        <div class="skeleton mt-4 h-60"></div>
      }
    </div>
  `,
})
export class TripPage {
  private readonly api = inject(DriversApi);
  private readonly payments = inject(PaymentsApi);
  private readonly checkout = inject(CheckoutService);
  private readonly toast = inject(ToastService);
  protected readonly features = inject(FeaturesService);

  readonly id = input.required<string>();

  protected readonly trip = signal<TripView | null>(null);
  protected readonly payment = signal<PaymentView | null>(null);
  protected readonly options = signal<PaymentOptions | null>(null);
  protected readonly preview = signal<RefundPreview | null>(null);
  protected readonly error = signal<string | null>(null);
  protected readonly busy = signal(false);
  protected readonly askCancel = signal(false);
  protected readonly reason = signal('');
  protected readonly code = signal('');
  protected readonly stars = signal(0);
  protected readonly comment = signal('');
  protected readonly useCredits = signal(true);
  protected readonly money = rupees;
  protected readonly cash = paise;
  protected readonly wa = whatsappLink;
  /** Re-checks the time windows (set off, start) while the page is open. */
  private readonly now = signal(Date.now());

  protected readonly state = computed(() => tripStatus(this.trip()!));
  protected readonly other = computed(() => {
    const t = this.trip()!;
    return t.viewer === 'DRIVER' ? t.customer : t.driver;
  });
  protected readonly length = computed(() => {
    const t = this.trip()!;
    return tripLength(t.kind, t.units);
  });
  protected readonly canSetOff = computed(() => {
    const t = this.trip();
    return !!t && t.viewer === 'DRIVER' && t.status === (t.payOnline ? 'PAID' : 'ACCEPTED')
      && this.now() >= Date.parse(t.from) - 3 * 3600_000;
  });
  protected readonly canStart = computed(() => {
    const t = this.trip();
    if (!t || t.viewer === 'STAFF') return false;
    const ready = t.payOnline ? t.status === 'PAID' || t.status === 'ON_THE_WAY' : t.status === 'ACCEPTED' || t.status === 'ON_THE_WAY';
    return ready && this.now() >= Date.parse(t.from) - 3600_000 && (t.viewer === 'CUSTOMER' || t.wrongCodes < 5);
  });
  protected readonly canCancel = computed(() => {
    const t = this.trip();
    if (!t || t.viewer === 'STAFF') return false;
    if (t.viewer === 'DRIVER') return ['ACCEPTED', 'PAID', 'ON_THE_WAY'].includes(t.status);
    return ['REQUESTED', 'ACCEPTED', 'PAID', 'ON_THE_WAY'].includes(t.status);
  });

  constructor() {
    effect(() => {
      const id = this.id();
      untracked(() => void this.load(id));
    });
    const timer = setInterval(() => this.now.set(Date.now()), 30_000);
    inject(DestroyRef).onDestroy(() => clearInterval(timer));
  }

  protected async act(action: 'accept' | 'decline' | 'on-the-way' | 'end', done: string): Promise<void> {
    await this.run(() => this.api.tripAction(this.id(), action), done);
  }

  protected async start(code: string | null): Promise<void> {
    const ok = await this.run(() => this.api.start(this.id(), code), 'Trip started. Have a safe drive!');
    if (!ok) await this.load(this.id());
    this.code.set('');
  }

  protected async openCancel(): Promise<void> {
    this.askCancel.set(true);
    this.preview.set(null);
    if (this.trip()?.viewer === 'CUSTOMER' && this.payment()?.status === 'PAID') {
      try {
        this.preview.set(await this.payments.refundPreview(this.id()));
      } catch {
        // The question still works.
      }
    }
  }

  protected async cancel(): Promise<void> {
    const ok = await this.run(() => this.api.cancel(this.id(), this.reason().trim() || null), 'Cancelled.');
    if (ok) this.askCancel.set(false);
  }

  protected async rate(): Promise<void> {
    await this.run(() => this.api.review(this.id(), this.stars(), this.comment().trim() || null), 'Thanks for rating!');
  }

  protected async pay(): Promise<void> {
    const t = this.trip();
    if (!t) return;
    this.busy.set(true);
    try {
      const view = await this.api.pay(t.id, this.useCredits() && !!this.options()?.credits);
      if (view.status !== 'PAID') {
        const answer = await this.checkout.pay(view);
        if (!answer) {
          this.toast.info('Payment not finished. You can pay again from this page.');
          return;
        }
        await this.payments.confirm(view.paymentId, answer);
      }
      this.toast.success('Paid! Your driver is confirmed.');
    } catch (e) {
      this.toast.error(e instanceof Error && !('status' in e) ? e.message : problemOf(e).detail);
    } finally {
      this.busy.set(false);
      await this.load(t.id);
    }
  }

  private async run(call: () => Promise<TripView>, done: string): Promise<boolean> {
    this.busy.set(true);
    try {
      this.trip.set(await call());
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
      this.trip.set(await this.api.trip(id));
      await this.loadPayment();
    } catch (e) {
      this.error.set(problemOf(e).detail);
    }
  }

  private async loadPayment(): Promise<void> {
    const t = this.trip();
    if (!t?.payOnline) return;
    try {
      this.payment.set((await this.payments.forBookings([t.id]))[0] ?? null);
      if (t.viewer === 'CUSTOMER' && !this.options()) this.options.set(await this.payments.options());
    } catch {
      // The rest of the page still works.
    }
  }
}
