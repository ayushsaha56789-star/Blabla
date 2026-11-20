import { DatePipe, Location } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, effect, inject, input, numberAttribute, signal, untracked } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { ApiProblem } from '../../core/api.models';
import { AuthService } from '../../core/auth.service';
import { ChatApi } from '../../core/chat-api';
import { problemOf } from '../../core/errors';
import { FeaturesService } from '../../core/features.service';
import { bookingStatus, duration, rupees, seatsLeftFor, whatsappLink } from '../../core/rides-format';
import { BookingStatus, PassengerView, RideDetails } from '../../core/rides.models';
import { RidesApi } from '../../core/rides-api';
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
  private readonly location = inject(Location);
  private readonly toast = inject(ToastService);
  private readonly trips = inject(TripsService);
  protected readonly auth = inject(AuthService);
  protected readonly features = inject(FeaturesService);

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
    return b && (b.status === 'REQUESTED' || b.status === 'CONFIRMED') ? b : null;
  });
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
    } catch (e) {
      this.problem.set(problemOf(e));
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
