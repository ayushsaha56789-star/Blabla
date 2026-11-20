import { DatePipe, Location } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, effect, inject, input, numberAttribute, signal, untracked } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { ApiProblem } from '../../core/api.models';
import { AuthService } from '../../core/auth.service';
import { ChatApi } from '../../core/chat-api';
import { problemOf } from '../../core/errors';
import { FeaturesService } from '../../core/features.service';
import { Game, GamesApi } from '../../core/games-api';
import { bookingStatus, duration, rupees, seatsLeftFor, whatsappLink } from '../../core/rides-format';
import { BookingStatus, PassengerView, RideDetails } from '../../core/rides.models';
import { RidesApi } from '../../core/rides-api';
import { CheckoutService } from '../../core/checkout.service';
import { CheckoutView, feeFor, paise, PaymentOptions, PaymentsApi, PaymentView, RefundPreview } from '../../core/payments-api';
import { ToastService } from '../../core/toast.service';
import { TripsService } from '../../core/trips.service';
import { Avatar } from '../../shared/avatar';
import { Icon } from '../../shared/icon';
import { PhonePipe } from '../../shared/phone.pipe';
import { Stars } from '../../shared/stars';
import { TripSafety } from './trip-safety';
import { ReportDialog } from '../../shared/report-dialog';
import { chatOption, preferenceBadges } from '../profile/prefs';

const optionalNumber = (v: unknown) => (v == null || v === '' ? undefined : numberAttribute(v));

/** One ride: route, driver, car and passengers, plus booking (passengers) or managing requests (the driver). */
@Component({
  selector: 'app-ride-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [DatePipe, RouterLink, Icon, Avatar, PhonePipe, Stars, TripSafety, ReportDialog],
  templateUrl: './ride-page.html',
})
export class RidePage {
  private readonly api = inject(RidesApi);
  private readonly chat = inject(ChatApi);
  private readonly router = inject(Router);
  private readonly games = inject(GamesApi);
  private readonly location = inject(Location);
  private readonly toast = inject(ToastService);
  private readonly trips = inject(TripsService);
  private readonly payments = inject(PaymentsApi);
  private readonly checkout = inject(CheckoutService);
  protected readonly auth = inject(AuthService);
  protected readonly features = inject(FeaturesService);

  /** How the member can pay, their payment for this ride, and what cancelling would give back. */
  protected readonly payOptions = signal<PaymentOptions | null>(null);
  protected readonly payNow = signal(true);
  protected readonly useCredits = signal(true);
  protected readonly myPayment = signal<PaymentView | null>(null);
  protected readonly refundPreview = signal<RefundPreview | null>(null);
  protected readonly money = paise;

  readonly id = input.required<string>();
  /** The stretch and seats chosen in search results. */
  readonly from = input<number | undefined, unknown>(undefined, { transform: optionalNumber });
  readonly to = input<number | undefined, unknown>(undefined, { transform: optionalNumber });
  readonly seats = input<number | undefined, unknown>(undefined, { transform: optionalNumber });

  protected readonly ride = signal<RideDetails | null>(null);
  protected readonly problem = signal<ApiProblem | null>(null);
  protected readonly busy = signal(false);
  protected readonly fromPos = signal(0);
  protected readonly toPos = signal(1);
  protected readonly seatsWanted = signal(1);
  protected readonly message = signal('');
  protected readonly confirmCancelBooking = signal(false);
  protected readonly cancellingRide = signal(false);
  protected readonly cancelReason = signal('');
  protected readonly reporting = signal(false);

  protected readonly fmtRupees = rupees;
  protected readonly fmtDuration = duration;
  protected readonly waLink = whatsappLink;
  protected readonly chatOption = chatOption;
  protected readonly prefBadges = preferenceBadges;

  protected readonly last = computed(() => (this.ride()?.stops.length ?? 1) - 1);
  protected readonly price = computed(() => this.ride()?.prices[this.fromPos()]?.[this.toPos()] ?? 0);
  protected readonly seatsLeft = computed(() => {
    const r = this.ride();
    return r ? seatsLeftFor(r.seatsLeftByLeg, this.fromPos(), this.toPos()) : 0;
  });
  protected readonly maxSeats = computed(() => Math.min(4, this.seatsLeft()));
  protected readonly pickupPassed = computed(() => {
    const r = this.ride();
    return !r || Date.parse(r.stops[this.fromPos()].at) <= Date.now();
  });
  protected readonly canBook = computed(() => !!this.ride()?.bookable && this.seatsLeft() > 0 && !this.pickupPassed());
  protected readonly myStatus = computed(() => {
    const b = this.ride()?.myBooking;
    return b ? bookingStatus(b.status, b.cancelledBy) : null;
  });
  protected readonly liveBooking = computed(() => {
    const b = this.ride()?.myBooking;
    return b && (b.status === 'AWAITING_PAYMENT' || b.status === 'REQUESTED' || b.status === 'CONFIRMED') ? b : null;
  });
  /** Paying online for this booking (the owner turned it on and the member chose it, or it's the only way). */
  protected readonly online = computed(() => {
    const o = this.payOptions();
    return !!o?.online && (this.payNow() || !o.cash);
  });
  protected readonly seatPaise = computed(() => this.price() * this.seatsWanted() * 100);
  protected readonly feePaise = computed(() => (this.online() ? feeFor(this.seatPaise(), this.payOptions()?.serviceFeePercent ?? 0) : 0));
  /** Credits the server will use, worked out the same way (it keeps at least ₹1 for the gateway). */
  protected readonly creditsToUse = computed(() => {
    const o = this.payOptions();
    if (!this.online() || !o?.credits || !this.useCredits() || o.creditsPaise <= 0) return 0;
    const total = this.seatPaise() + this.feePaise();
    let credits = Math.min(o.creditsPaise, total);
    if (credits < total && total - credits < 100) credits = Math.max(0, total - 100);
    return credits;
  });
  protected readonly payNowPaise = computed(() => this.seatPaise() + this.feePaise() - this.creditsToUse());
  protected readonly requests = computed(() => this.ride()?.passengers.filter((p) => p.status === 'REQUESTED') ?? []);
  protected readonly confirmed = computed(() => this.ride()?.passengers.filter((p) => p.status !== 'REQUESTED') ?? []);
  protected readonly seatsBooked = computed(() => this.confirmed().reduce((n, p) => n + p.seats, 0));
  protected readonly isUpcoming = computed(() => this.ride()?.status === 'SCHEDULED');

  constructor() {
    effect(() => {
      const id = this.id();
      this.auth.user()?.id;
      untracked(() => void this.load(id, true));
    });
  }

  protected inStretch(position: number): boolean {
    return position >= this.fromPos() && position <= this.toPos();
  }

  protected stopName(position: number): string {
    return this.ride()?.stops[position]?.name ?? '';
  }

  protected setFrom(value: string): void {
    const from = Number(value);
    this.fromPos.set(from);
    if (this.toPos() <= from) this.toPos.set(from + 1);
    this.clampSeats();
  }

  protected setTo(value: string): void {
    this.toPos.set(Number(value));
    this.clampSeats();
  }

  protected passengerStretch(p: PassengerView): string {
    return `${this.stopName(p.fromPosition)} → ${this.stopName(p.toPosition)}`;
  }

  protected statusOf(status: BookingStatus) {
    return bookingStatus(status);
  }

  /** Opens (or joins) the game room for the people on this ride. */
  protected async playOnRide(game: Game): Promise<void> {
    this.busy.set(true);
    try {
      const room = await this.games.ride(this.id(), game);
      void this.router.navigate(['/games', room.id]);
    } catch (e) {
      this.toast.error(problemOf(e).detail);
    } finally {
      this.busy.set(false);
    }
  }

  protected back(): void {
    if (history.length > 1) this.location.back();
    else void this.router.navigate(['/search']);
  }

  protected async book(): Promise<void> {
    const r = this.ride();
    if (!r) return;
    if (!this.auth.isLoggedIn()) {
      void this.router.navigate(['/login'], { queryParams: { returnUrl: this.router.url } });
      return;
    }
    if (!this.auth.user()?.profileComplete) {
      void this.router.navigate(['/onboarding'], { queryParams: { returnUrl: this.router.url } });
      return;
    }
    if (this.online()) {
      await this.bookAndPay(r);
      return;
    }
    this.busy.set(true);
    try {
      const b = await this.api.book(r.id, {
        fromPosition: this.fromPos(),
        toPosition: this.toPos(),
        seats: this.seatsWanted(),
        message: this.message().trim() || null,
      });
      this.toast.success(b.status === 'CONFIRMED' ? 'Your seat is booked! Have a great trip.' : `Request sent. We'll let ${r.driver.profile.displayName} decide.`);
      this.message.set('');
      await this.load(r.id, false);
      void this.trips.refresh();
    } catch (e) {
      this.toast.error(problemOf(e).detail);
      await this.load(r.id, false);
    } finally {
      this.busy.set(false);
    }
  }

  /** Opens the chat with the driver (asking a question before booking, or after). */
  protected async messageDriver(): Promise<void> {
    if (!this.auth.isLoggedIn()) {
      void this.router.navigate(['/login'], { queryParams: { returnUrl: this.router.url } });
      return;
    }
    await this.openChat(() => this.chat.open(this.id()));
  }

  protected async messagePassenger(p: PassengerView): Promise<void> {
    if (p.member.id) await this.openChat(() => this.chat.open(this.id(), p.member.id!));
  }

  private async openChat(open: () => Promise<{ id: string }>): Promise<void> {
    this.busy.set(true);
    try {
      const c = await open();
      void this.router.navigate(['/messages', c.id]);
    } catch (e) {
      this.toast.error(problemOf(e).detail);
    } finally {
      this.busy.set(false);
    }
  }

  /** Holds the seat, then opens the payment window. */
  private async bookAndPay(r: RideDetails): Promise<void> {
    this.busy.set(true);
    try {
      const view = await this.payments.checkout({
        rideId: r.id,
        fromPosition: this.fromPos(),
        toPosition: this.toPos(),
        seats: this.seatsWanted(),
        message: this.message().trim() || null,
        useCredits: this.creditsToUse() > 0,
      });
      this.message.set('');
      if (view.status === 'PAID') {
        this.toast.success(r.instantBooking ? 'Paid with your credits. Your seat is booked!' : 'Paid with your credits. Request sent to the driver.');
      } else {
        await this.finishPayment(view, r);
      }
    } catch (e) {
      this.toast.error(problemOf(e).detail);
    } finally {
      await this.load(r.id, false);
      void this.trips.refresh();
      this.busy.set(false);
    }
  }

  /** Back to a payment that wasn't finished (the seat is still held). */
  protected async completePayment(): Promise<void> {
    const p = this.myPayment();
    const r = this.ride();
    if (!p || !r) return;
    this.busy.set(true);
    try {
      await this.finishPayment(await this.payments.resume(p.id), r);
    } catch (e) {
      this.toast.error(problemOf(e).detail);
    } finally {
      await this.load(r.id, false);
      void this.trips.refresh();
      this.busy.set(false);
    }
  }

  private async finishPayment(view: CheckoutView, r: RideDetails): Promise<void> {
    let answer;
    try {
      answer = await this.checkout.pay(view);
    } catch (e) {
      this.toast.error((e as Error).message);
      return;
    }
    if (!answer) {
      const until = new Date(view.holdUntil).toLocaleTimeString('en-IN', { hour: 'numeric', minute: '2-digit' });
      this.toast.info(`Payment not finished. Your seat is held until ${until}: finish paying here or from My rides.`);
      return;
    }
    const paid = await this.payments.confirm(view.paymentId, answer);
    if (!paid.seatHeld) {
      this.toast.error('Your payment arrived after the seat was released, so we are refunding it in full.');
    } else if (r.instantBooking) {
      this.toast.success('Paid! Your seat is booked. Have a great trip.');
    } else {
      this.toast.success(`Paid! ${r.driver.profile.displayName} will accept or decline your request. If they decline, you get everything back.`);
    }
  }

  /** Opens the cancel question, with what the member would get back. */
  protected async askCancel(): Promise<void> {
    this.confirmCancelBooking.set(true);
    this.refundPreview.set(null);
    const b = this.liveBooking();
    if (b && this.myPayment()?.status === 'PAID') {
      try {
        this.refundPreview.set(await this.payments.refundPreview(b.id));
      } catch {
        // The question still works without the preview.
      }
    }
  }

  protected async cancelMyBooking(): Promise<void> {
    const b = this.liveBooking();
    const r = this.ride();
    if (!b || !r) return;
    await this.act(() => this.api.cancelBooking(b.id), 'Your booking is cancelled.');
    this.confirmCancelBooking.set(false);
  }

  protected async answer(p: PassengerView, accept: boolean): Promise<void> {
    await this.act(
      () => (accept ? this.api.acceptBooking(p.bookingId) : this.api.declineBooking(p.bookingId)),
      accept ? `${p.member.displayName} is coming along!` : 'Request declined.',
    );
  }

  protected async cancelRide(): Promise<void> {
    const r = this.ride();
    if (!r) return;
    await this.act(() => this.api.cancelRide(r.id, this.cancelReason().trim() || null), 'Your ride is cancelled. Passengers can see it in My rides.');
    this.cancellingRide.set(false);
  }

  protected async share(): Promise<void> {
    const r = this.ride();
    if (!r) return;
    const url = `${location.origin}/rides/${r.id}`;
    const title = `${r.stops[0].name} → ${r.stops[r.stops.length - 1].name} on SaathChalo`;
    try {
      if (navigator.share) {
        await navigator.share({ title, url });
      } else {
        await navigator.clipboard.writeText(url);
        this.toast.success('Link copied. Share it with friends who travel this way.');
      }
    } catch {
      // The person closed the share sheet.
    }
  }

  private async act(call: () => Promise<unknown>, done: string): Promise<void> {
    const r = this.ride();
    if (!r) return;
    this.busy.set(true);
    try {
      await call();
      this.toast.success(done);
    } catch (e) {
      this.toast.error(problemOf(e).detail);
    } finally {
      await this.load(r.id, false);
      void this.trips.refresh();
      this.busy.set(false);
    }
  }

  private async load(id: string, first: boolean): Promise<void> {
    try {
      const r = await this.api.ride(id);
      this.ride.set(r);
      this.problem.set(null);
      if (first) this.initStretch(r);
      this.clampSeats();
      void this.loadPayments(r);
    } catch (e) {
      this.problem.set(problemOf(e));
    }
  }

  private async loadPayments(r: RideDetails): Promise<void> {
    if (!this.auth.isLoggedIn() || r.viewer === 'DRIVER') return;
    try {
      this.payOptions.set(await this.payments.options());
      this.myPayment.set(r.myBooking ? ((await this.payments.forBookings([r.myBooking.id]))[0] ?? null) : null);
    } catch {
      // Paying the driver directly still works.
    }
  }

  private initStretch(r: RideDetails): void {
    const last = r.stops.length - 1;
    const own = r.myBooking;
    let from = own ? own.fromPosition : (this.from() ?? 0);
    let to = own ? own.toPosition : (this.to() ?? last);
    if (!(from >= 0 && from < last)) from = 0;
    if (!(to > from && to <= last)) to = last;
    this.fromPos.set(from);
    this.toPos.set(to);
    this.seatsWanted.set(Math.max(1, Math.min(this.seats() ?? 1, 4)));
  }

  private clampSeats(): void {
    const max = Math.max(1, this.maxSeats());
    if (this.seatsWanted() > max) this.seatsWanted.set(max);
  }
}
