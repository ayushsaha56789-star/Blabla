import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, effect, inject, input, numberAttribute, OnInit, signal, untracked } from '@angular/core';
import { RouterLink } from '@angular/router';
import { AuthService } from '../../core/auth.service';
import { problemOf } from '../../core/errors';
import { FeaturesService } from '../../core/features.service';
import { addDays, dayOf, duration, rupees, stretchPrice } from '../../core/rides-format';
import { Place, Quote, RideDetails, Vehicle } from '../../core/rides.models';
import { RidesApi } from '../../core/rides-api';
import { ToastService } from '../../core/toast.service';
import { TripsService } from '../../core/trips.service';
import { todayIso } from '../../core/util';
import { Icon } from '../../shared/icon';
import { PlaceInput } from '../../shared/place-input';
import { VehicleForm } from '../../shared/vehicle-form';

type Step = 'route' | 'when' | 'car' | 'price' | 'review';

interface Stop {
  place: Place;
  meetingPoint: string;
}

const optionalNumber = (v: unknown) => (v == null || v === '' ? undefined : numberAttribute(v));

/** Offer a ride in five short steps. Prefill with /publish?fromId=…&toId=… */
@Component({
  selector: 'app-publish-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [DatePipe, RouterLink, Icon, PlaceInput, VehicleForm],
  templateUrl: './publish-page.html',
})
export class PublishPage implements OnInit {
  private readonly api = inject(RidesApi);
  private readonly toast = inject(ToastService);
  private readonly trips = inject(TripsService);
  protected readonly auth = inject(AuthService);
  protected readonly features = inject(FeaturesService);

  readonly fromId = input<number | undefined, unknown>(undefined, { transform: optionalNumber });
  readonly toId = input<number | undefined, unknown>(undefined, { transform: optionalNumber });

  protected readonly steps: ReadonlyArray<{ key: Step; label: string }> = [
    { key: 'route', label: 'Route' },
    { key: 'when', label: 'When' },
    { key: 'car', label: 'Car' },
    { key: 'price', label: 'Price' },
    { key: 'review', label: 'Publish' },
  ];
  protected readonly step = signal<Step>('route');
  protected readonly stepIndex = computed(() => this.steps.findIndex((s) => s.key === this.step()));

  protected readonly today = todayIso();
  protected readonly tomorrow = addDays(todayIso(), 1);
  protected readonly lastDay = addDays(todayIso(), 90);

  // Route
  protected readonly from = signal<Place | null>(null);
  protected readonly to = signal<Place | null>(null);
  protected readonly fromMeeting = signal('');
  protected readonly toMeeting = signal('');
  protected readonly stops = signal<Stop[]>([]);
  protected readonly newStop = signal<Place | null>(null);
  protected readonly suggestions = signal<Place[]>([]);
  protected readonly quote = signal<Quote | null>(null);
  protected readonly showMeetingPoints = signal(false);

  // When
  protected readonly date = signal(addDays(todayIso(), 1));
  protected readonly time = signal('08:00');

  // Car and seats
  protected readonly cars = signal<Vehicle[] | null>(null);
  protected readonly carId = signal<string | null>(null);
  protected readonly addingCar = signal(false);
  protected readonly seats = signal(3);

  // Price and options
  protected readonly price = signal<number | null>(null);
  protected readonly instant = signal(true);
  protected readonly ladiesOnly = signal(false);
  protected readonly notes = signal('');

  protected readonly error = signal<string | null>(null);
  protected readonly publishing = signal(false);
  protected readonly published = signal<RideDetails | null>(null);

  protected readonly fmtRupees = rupees;
  protected readonly fmtDuration = duration;
  protected readonly dayOf = dayOf;

  protected readonly allPlaces = computed(() => {
    const from = this.from();
    const to = this.to();
    return from && to ? [from, ...this.stops().map((s) => s.place), to] : [];
  });
  protected readonly usedIds = computed(() => this.allPlaces().map((p) => p.id));
  protected readonly openSuggestions = computed(() => {
    const used = new Set(this.usedIds());
    return this.suggestions().filter((p) => !used.has(p.id));
  });
  protected readonly car = computed(() => this.cars()?.find((c) => c.id === this.carId()) ?? null);
  protected readonly isWoman = computed(() => this.auth.user()?.gender === 'FEMALE');
  protected readonly canLadiesOnly = computed(() => this.isWoman() && this.features.isOn('rides.ladies_only'));
  /** Departure as a moment in time (rides are always entered in Indian time), or null while incomplete. */
  protected readonly departure = computed(() => {
    const d = new Date(`${this.date()}T${this.time()}:00+05:30`);
    return Number.isNaN(d.getTime()) ? null : d;
  });
  protected readonly arrival = computed(() => {
    const q = this.quote();
    const dep = this.departure();
    return q && dep ? new Date(dep.getTime() + q.durationMin * 60_000) : null;
  });
  protected readonly stretchPrices = computed(() => {
    const q = this.quote();
    const price = this.price();
    const places = this.allPlaces();
    if (!q || price == null || places.length < 3) return [];
    const rows: { label: string; price: number }[] = [];
    for (let i = 0; i < places.length - 1; i++) {
      rows.push({ label: `${places[i].name} → ${places[i + 1].name}`, price: stretchPrice(price, q.kmFromStart, i, i + 1) });
    }
    return rows;
  });

  private quoteNo = 0;

  constructor() {
    // Re-estimate the route whenever it changes, and suggest towns on the way.
    effect(() => {
      const ids = this.usedIds();
      untracked(() => void this.refreshQuote(ids));
    });
    effect(() => {
      const from = this.from();
      const to = this.to();
      untracked(() => void this.loadSuggestions(from, to));
    });
    effect(() => {
      const stop = this.newStop();
      if (stop) untracked(() => this.addStop(stop));
    });
  }

  ngOnInit(): void {
    void this.prefill();
    void this.loadCars();
  }

  // ----- Navigation -----

  protected go(step: Step): void {
    if (this.steps.findIndex((s) => s.key === step) > this.stepIndex() && !this.validate()) return;
    this.error.set(null);
    this.step.set(step);
    window.scrollTo({ top: 0, behavior: 'smooth' });
  }

  protected next(): void {
    const i = this.stepIndex();
    if (i < this.steps.length - 1) this.go(this.steps[i + 1].key);
  }

  protected prev(): void {
    const i = this.stepIndex();
    if (i > 0) this.go(this.steps[i - 1].key);
  }

  /** Checks the current step before moving on, with a friendly message. */
  private validate(): boolean {
    const fail = (msg: string) => {
      this.error.set(msg);
      return false;
    };
    switch (this.step()) {
      case 'route':
        if (!this.from()) return fail('Where does your ride start? Pick a town from the list.');
        if (!this.to()) return fail('Where are you going? Pick a town from the list.');
        if (this.from()!.id === this.to()!.id) return fail('Your start and destination are the same.');
        if (!this.quote()) return fail('One moment, we are working out the route…');
        if (this.quote()!.distanceKm < 10) return fail('Rides must be at least 10 km long.');
        return true;
      case 'when':
        if (!this.departure()) return fail('Please choose a date and time.');
        if (this.departure()!.getTime() < Date.now() + 15 * 60_000) return fail('Please choose a time at least 15 minutes from now.');
        if (this.date() > this.lastDay) return fail('You can offer rides up to 90 days ahead.');
        return true;
      case 'car':
        if (!this.car()) return fail('Please choose your car, or add one.');
        return true;
      case 'price': {
        const q = this.quote();
        const p = this.price();
        if (!q || p == null || Number.isNaN(p)) return fail('Please set a price per seat.');
        if (p < q.minPrice) return fail(`The lowest price per seat is ${rupees(q.minPrice)}.`);
        if (p > q.maxPrice) return fail(`SaathChalo is for sharing costs, so the most you can ask for this route is ${rupees(q.maxPrice)} per seat.`);
        return true;
      }
      default:
        return true;
    }
  }

  // ----- Route -----

  protected addStop(place: Place): void {
    const from = this.from();
    if (this.usedIds().includes(place.id)) {
      this.toast.info(`${place.name} is already on your route.`);
    } else if (this.stops().length >= 6) {
      this.toast.info('You can add up to 6 stops.');
    } else {
      // Keep stops in travel order: closest to the start first.
      const dist = (p: Place) => (from ? (p.lat - from.lat) ** 2 + ((p.lng - from.lng) * Math.cos((from.lat * Math.PI) / 180)) ** 2 : 0);
      this.stops.update((list) => [...list, { place, meetingPoint: '' }].sort((a, b) => dist(a.place) - dist(b.place)));
    }
    queueMicrotask(() => this.newStop.set(null));
  }

  protected removeStop(index: number): void {
    this.stops.update((list) => list.filter((_, i) => i !== index));
  }

  protected setStopMeeting(index: number, value: string): void {
    this.stops.update((list) => list.map((s, i) => (i === index ? { ...s, meetingPoint: value } : s)));
  }

  protected swapEnds(): void {
    const from = this.from();
    const fromMeeting = this.fromMeeting();
    this.from.set(this.to());
    this.to.set(from);
    this.fromMeeting.set(this.toMeeting());
    this.toMeeting.set(fromMeeting);
    this.stops.update((list) => [...list].reverse());
  }

  // ----- Car and seats -----

  protected onCarSaved(car: Vehicle): void {
    this.cars.update((list) => [...(list ?? []).filter((c) => c.id !== car.id), car]);
    this.chooseCar(car);
    this.addingCar.set(false);
    this.toast.success(`${car.make} ${car.model} added.`);
  }

  protected chooseCar(car: Vehicle): void {
    this.carId.set(car.id);
    this.seats.set(Math.min(Math.max(this.seats(), 1), car.seats));
  }

  // ----- Price -----

  protected setPrice(value: number): void {
    const q = this.quote();
    if (!q || Number.isNaN(value)) return;
    this.price.set(Math.round(Math.min(q.maxPrice, Math.max(q.minPrice, value))));
  }

  protected nudgePrice(delta: number): void {
    this.setPrice((this.price() ?? this.quote()?.suggestedPrice ?? 0) + delta);
  }

  // ----- Publish -----

  protected async publish(): Promise<void> {
    const car = this.car();
    const from = this.from();
    const to = this.to();
    const price = this.price();
    if (!car || !from || !to || price == null) return;
    this.publishing.set(true);
    this.error.set(null);
    try {
      const ride = await this.api.publish({
        vehicleId: car.id,
        stops: [
          { placeId: from.id, meetingPoint: this.fromMeeting().trim() || null },
          ...this.stops().map((s) => ({ placeId: s.place.id, meetingPoint: s.meetingPoint.trim() || null })),
          { placeId: to.id, meetingPoint: this.toMeeting().trim() || null },
        ],
        date: this.date(),
        time: this.time(),
        seats: this.seats(),
        pricePerSeat: price,
        instantBooking: this.instant(),
        ladiesOnly: this.canLadiesOnly() && this.ladiesOnly(),
        notes: this.notes().trim() || null,
      });
      this.published.set(ride);
      void this.trips.refresh();
      window.scrollTo({ top: 0, behavior: 'smooth' });
    } catch (e) {
      const p = problemOf(e);
      this.error.set(p.detail);
      const stepFor: Record<string, Step> = {
        route_too_short: 'route', duplicate_stop: 'route', place_not_found: 'route',
        departure_too_soon: 'when', departure_too_far: 'when', ride_overlap: 'when',
        vehicle_not_found: 'car', too_many_seats: 'car', price_too_high: 'price', price_too_low: 'price',
      };
      if (p.code && stepFor[p.code]) this.step.set(stepFor[p.code]);
    } finally {
      this.publishing.set(false);
    }
  }

  /** Starts a new ride going the other way, keeping the car, seats and price. */
  protected offerReturn(): void {
    this.swapEnds();
    this.published.set(null);
    this.date.set(addDays(this.date(), 1));
    this.step.set('when');
    window.scrollTo({ top: 0, behavior: 'smooth' });
  }

  protected offerAnother(): void {
    this.published.set(null);
    this.from.set(null);
    this.to.set(null);
    this.stops.set([]);
    this.fromMeeting.set('');
    this.toMeeting.set('');
    this.notes.set('');
    this.price.set(null);
    this.step.set('route');
  }

  protected async share(): Promise<void> {
    const ride = this.published();
    if (!ride) return;
    const url = `${location.origin}/rides/${ride.id}`;
    try {
      if (navigator.share) await navigator.share({ title: 'My ride on SaathChalo', url });
      else {
        await navigator.clipboard.writeText(url);
        this.toast.success('Link copied. Share it on WhatsApp groups to fill your seats.');
      }
    } catch {
      // Closed the share sheet.
    }
  }

  // ----- Loading -----

  private async prefill(): Promise<void> {
    const [fromId, toId] = [this.fromId(), this.toId()];
    try {
      if (fromId) this.from.set(await this.api.place(fromId));
      if (toId) this.to.set(await this.api.place(toId));
    } catch {
      // A stale link; the member can pick the places themselves.
    }
  }

  private async loadCars(): Promise<void> {
    try {
      const cars = await this.api.myVehicles();
      this.cars.set(cars);
      if (cars.length) this.chooseCar(cars[cars.length - 1]);
    } catch (e) {
      this.cars.set([]);
      this.toast.error(problemOf(e).detail);
    }
  }

  private async refreshQuote(ids: number[]): Promise<void> {
    const no = ++this.quoteNo;
    if (ids.length < 2 || ids[0] === ids[ids.length - 1]) {
      this.quote.set(null);
      return;
    }
    try {
      const q = await this.api.quote(ids);
      if (no !== this.quoteNo) return;
      this.quote.set(q);
      const p = this.price();
      if (p == null || p > q.maxPrice || p < q.minPrice) this.price.set(q.suggestedPrice);
    } catch (e) {
      if (no === this.quoteNo) {
        this.quote.set(null);
        this.error.set(problemOf(e).detail);
      }
    }
  }

  private async loadSuggestions(from: Place | null, to: Place | null): Promise<void> {
    this.suggestions.set([]);
    if (!from || !to || from.id === to.id) return;
    try {
      this.suggestions.set(await this.api.placesAlong(from.id, to.id));
    } catch {
      // Suggestions are optional.
    }
  }
}
