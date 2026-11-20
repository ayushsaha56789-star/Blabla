import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { CheckoutView } from './payments-api';
import { MemberCard } from './rides.models';
import { rupees } from './rides-format';

/** Driver partners and the trips members hire them for. Money in rupees. */
export type LicenceClass = 'LMV' | 'TRANSPORT';
export type TripKind = 'LOCAL' | 'OUTSTATION';
export type DriverSort = 'BEST' | 'PRICE' | 'NEAREST';
export type DriverStatus = 'DRAFT' | 'IN_REVIEW' | 'LIVE' | 'REJECTED' | 'REMOVED';
export type TripStatus = 'REQUESTED' | 'ACCEPTED' | 'PAID' | 'ON_THE_WAY' | 'STARTED' | 'COMPLETED' | 'DECLINED' | 'EXPIRED'
  | 'CANCELLED';
export type LicenceCheck = 'APPROVED' | 'PENDING' | 'REJECTED' | 'NONE';

export const LICENCE_LABELS: Record<LicenceClass, string> = {
  LMV: 'LMV (private cars)',
  TRANSPORT: 'Transport (commercial vehicles too)',
};

export interface Rates {
  hourPrice: number;
  dayPrice: number;
  outstationPrice: number;
  allowance: number;
  nightCharge: number;
}

export interface DriverCard {
  id: string;
  member: MemberCard;
  rating: number | null;
  reviewCount: number;
  tripsDone: number;
  experienceYears: number;
  languages: string[];
  manual: boolean;
  automatic: boolean;
  outstation: boolean;
  policeChecked: boolean;
  areas: string[];
  rates: Rates;
  distanceKm: number | null;
  fare: number | null;
}

export interface AreaView {
  id: string;
  placeId: number;
  placeName: string;
  state: string | null;
  radiusKm: number;
}

export interface BlockView {
  id: string;
  from: string;
  to: string;
  note: string | null;
}

export interface DriverDetail {
  driver: DriverCard;
  about: string | null;
  licenceClass: LicenceClass;
  areas: AreaView[];
  reviews: { authorName: string; stars: number; comment: string | null; at: string }[];
  mine: boolean;
}

export interface DriverQuote {
  available: boolean;
  problem: string | null;
  kind: TripKind;
  from: string;
  to: string | null;
  units: number;
  base: number;
  dayPriceApplied: boolean;
  night: number;
  allowance: number;
  fare: number;
  extraPrice: number;
}

export interface MyDriverProfile {
  status: DriverStatus;
  reviewNote: string | null;
  licenceStatus: LicenceCheck;
  licenceNumberMasked: string | null;
  licenceClass: LicenceClass;
  licenceValidUntil: string;
  experienceYears: number;
  languages: string[];
  manual: boolean;
  automatic: boolean;
  outstation: boolean;
  rates: Rates;
  about: string | null;
  policeCertificate: boolean;
  policeChecked: boolean;
  takingBookings: boolean;
  areas: AreaView[];
  blocks: BlockView[];
  rating: number | null;
  reviewCount: number;
  tripsDone: number;
  requestsWaiting: number;
  hasPhoto: boolean;
}

export interface DriverRequest {
  licenceNumber: string | null;
  licenceClass: LicenceClass;
  licenceValidUntil: string;
  experienceYears: number;
  languages: string[];
  manual: boolean;
  automatic: boolean;
  outstation: boolean;
  hourPrice: number;
  dayPrice: number;
  outstationPrice: number;
  allowance: number;
  nightCharge: number;
  about: string | null;
}

export interface TripRequest {
  driverId: string;
  kind: TripKind;
  from: string;
  hours: number | null;
  days: number | null;
  placeId: number;
  pickupAddress: string;
  destinationId: number | null;
  car: string;
  automatic: boolean;
  message: string | null;
}

export interface TripView {
  id: string;
  viewer: 'CUSTOMER' | 'DRIVER' | 'STAFF';
  driver: MemberCard;
  driverRating: number | null;
  driverReviews: number;
  customer: MemberCard;
  customerRating: number | null;
  kind: TripKind;
  from: string;
  to: string;
  units: number;
  placeName: string;
  pickupAddress: string | null;
  destinationName: string | null;
  car: string;
  automatic: boolean;
  fare: number;
  extraPrice: number;
  extraUnits: number;
  payOnline: boolean;
  payBy: string | null;
  status: TripStatus;
  startCode: string | null;
  wrongCodes: number;
  otherPhone: string | null;
  message: string | null;
  answerBy: string;
  createdAt: string;
  decidedAt: string | null;
  onTheWayAt: string | null;
  startedAt: string | null;
  endedAt: string | null;
  cancelledAt: string | null;
  cancelledBy: 'CUSTOMER' | 'DRIVER' | 'STAFF' | 'SYSTEM' | null;
  cancelReason: string | null;
  myStars: number | null;
  canReview: boolean;
}

export interface AdminDriverRow {
  id: string;
  member: MemberCard;
  status: DriverStatus;
  licenceStatus: LicenceCheck;
  licenceNumberMasked: string | null;
  licenceClass: LicenceClass;
  licenceValidUntil: string;
  experienceYears: number;
  languages: string[];
  manual: boolean;
  automatic: boolean;
  outstation: boolean;
  rates: Rates;
  about: string | null;
  areas: AreaView[];
  policeCertificate: boolean;
  policeChecked: boolean;
  takingBookings: boolean;
  reviewNote: string | null;
  updatedAt: string;
  tripsDone: number;
  rating: number | null;
}

export interface DriverSearch {
  placeId: number;
  kind: TripKind;
  from: string;
  hours: number | null;
  days: number | null;
  automatic: boolean;
  womenOnly: boolean;
  sort: DriverSort;
}

@Injectable({ providedIn: 'root' })
export class DriversApi {
  private readonly http = inject(HttpClient);

  search(s: DriverSearch): Promise<DriverCard[]> {
    let params = new HttpParams().set('placeId', s.placeId).set('kind', s.kind).set('from', s.from)
      .set('automatic', s.automatic).set('womenOnly', s.womenOnly).set('sort', s.sort);
    if (s.kind === 'LOCAL' && s.hours) params = params.set('hours', s.hours);
    if (s.kind === 'OUTSTATION' && s.days) params = params.set('days', s.days);
    return firstValueFrom(this.http.get<DriverCard[]>('/api/drivers/search', { params }));
  }

  profile(id: string): Promise<DriverDetail> {
    return firstValueFrom(this.http.get<DriverDetail>(`/api/drivers/profiles/${encodeURIComponent(id)}`));
  }

  quote(id: string, q: { placeId: number; kind: TripKind; from: string; hours: number | null; days: number | null; automatic: boolean }):
    Promise<DriverQuote> {
    let params = new HttpParams().set('placeId', q.placeId).set('kind', q.kind).set('from', q.from).set('automatic', q.automatic);
    if (q.kind === 'LOCAL' && q.hours) params = params.set('hours', q.hours);
    if (q.kind === 'OUTSTATION' && q.days) params = params.set('days', q.days);
    return firstValueFrom(this.http.get<DriverQuote>(`/api/drivers/profiles/${encodeURIComponent(id)}/quote`, { params }));
  }

  /** The member's own driver profile, or null if they haven't signed up. */
  async me(): Promise<MyDriverProfile | null> {
    const res = await firstValueFrom(this.http.get<MyDriverProfile>('/api/drivers/me', { observe: 'response' }));
    return res.status === 204 ? null : res.body;
  }

  save(body: DriverRequest): Promise<MyDriverProfile> {
    return firstValueFrom(this.http.put<MyDriverProfile>('/api/drivers/me', body));
  }

  addArea(placeId: number, radiusKm: number): Promise<MyDriverProfile> {
    return firstValueFrom(this.http.post<MyDriverProfile>('/api/drivers/me/areas', { placeId, radiusKm }));
  }

  removeArea(id: string): Promise<MyDriverProfile> {
    return firstValueFrom(this.http.delete<MyDriverProfile>(`/api/drivers/me/areas/${encodeURIComponent(id)}`));
  }

  policeCertificate(file: Blob): Promise<MyDriverProfile> {
    const form = new FormData();
    form.append('file', file, 'certificate.jpg');
    return firstValueFrom(this.http.post<MyDriverProfile>('/api/drivers/me/police-certificate', form));
  }

  removePoliceCertificate(): Promise<MyDriverProfile> {
    return firstValueFrom(this.http.delete<MyDriverProfile>('/api/drivers/me/police-certificate'));
  }

  submit(): Promise<MyDriverProfile> {
    return firstValueFrom(this.http.post<MyDriverProfile>('/api/drivers/me/submit', null));
  }

  takingBookings(on: boolean): Promise<MyDriverProfile> {
    return firstValueFrom(this.http.put<MyDriverProfile>('/api/drivers/me/taking-bookings', { on }));
  }

  addBlock(from: string, to: string, note: string | null): Promise<MyDriverProfile> {
    return firstValueFrom(this.http.post<MyDriverProfile>('/api/drivers/me/blocks', { from, to, note }));
  }

  removeBlock(id: string): Promise<MyDriverProfile> {
    return firstValueFrom(this.http.delete<MyDriverProfile>(`/api/drivers/me/blocks/${encodeURIComponent(id)}`));
  }

  leave(): Promise<void> {
    return firstValueFrom(this.http.delete<void>('/api/drivers/me'));
  }

  hire(body: TripRequest): Promise<TripView> {
    return firstValueFrom(this.http.post<TripView>('/api/drivers/trips', body));
  }

  myTrips(): Promise<{ hired: TripView[]; driving: TripView[] }> {
    return firstValueFrom(this.http.get<{ hired: TripView[]; driving: TripView[] }>('/api/drivers/trips'));
  }

  trip(id: string): Promise<TripView> {
    return firstValueFrom(this.http.get<TripView>(`/api/drivers/trips/${encodeURIComponent(id)}`));
  }

  tripAction(id: string, action: 'accept' | 'decline' | 'on-the-way' | 'end'): Promise<TripView> {
    return firstValueFrom(this.http.post<TripView>(`/api/drivers/trips/${encodeURIComponent(id)}/${action}`, null));
  }

  start(id: string, code: string | null): Promise<TripView> {
    return firstValueFrom(this.http.post<TripView>(`/api/drivers/trips/${encodeURIComponent(id)}/start`, { code }));
  }

  cancel(id: string, reason: string | null): Promise<TripView> {
    return firstValueFrom(this.http.post<TripView>(`/api/drivers/trips/${encodeURIComponent(id)}/cancel`, { reason }));
  }

  pay(id: string, useCredits: boolean): Promise<CheckoutView> {
    return firstValueFrom(this.http.post<CheckoutView>(`/api/drivers/trips/${encodeURIComponent(id)}/pay`, { useCredits }));
  }

  review(id: string, stars: number, comment: string | null): Promise<TripView> {
    return firstValueFrom(this.http.post<TripView>(`/api/drivers/trips/${encodeURIComponent(id)}/review`, { stars, comment }));
  }
}

/** How a trip's state reads, from the viewer's side. */
export function tripStatus(t: Pick<TripView, 'status' | 'payOnline' | 'viewer' | 'cancelledBy'>): { text: string; tone: string } {
  const driver = t.viewer === 'DRIVER';
  switch (t.status) {
    case 'REQUESTED': return { text: driver ? 'Waiting for your answer' : 'Waiting for the driver', tone: 'warning' };
    case 'ACCEPTED': return t.payOnline ? { text: driver ? 'Waiting for payment' : 'Accepted: pay to confirm', tone: 'warning' }
      : { text: 'Confirmed', tone: 'success' };
    case 'PAID': return { text: 'Confirmed and paid', tone: 'success' };
    case 'ON_THE_WAY': return { text: driver ? 'You’re on the way' : 'Driver on the way', tone: 'success' };
    case 'STARTED': return { text: 'Trip in progress', tone: 'success' };
    case 'COMPLETED': return { text: 'Completed', tone: 'muted' };
    case 'DECLINED': return { text: 'Declined', tone: 'danger' };
    case 'EXPIRED': return { text: 'Lapsed', tone: 'muted' };
    case 'CANCELLED':
      return {
        text: t.cancelledBy === 'DRIVER' ? (driver ? 'You cancelled' : 'Cancelled by the driver')
          : t.cancelledBy === 'CUSTOMER' ? (driver ? 'Cancelled by the member' : 'You cancelled')
            : t.cancelledBy === 'STAFF' ? 'Cancelled by SaathChalo' : 'Cancelled',
        tone: 'muted',
      };
  }
}

/** "6 hours" / "1 day" / "3 days". */
export function tripLength(kind: TripKind, units: number): string {
  return kind === 'LOCAL' ? `${units} ${units === 1 ? 'hour' : 'hours'}` : `${units} ${units === 1 ? 'day' : 'days'}`;
}

/** How a price was worked out, in a line people can check. */
export function fareLines(q: Pick<DriverQuote, 'kind' | 'units' | 'base' | 'dayPriceApplied' | 'night' | 'allowance'>, rates: Rates):
  { label: string; amount: number }[] {
  const lines: { label: string; amount: number }[] = [];
  if (q.kind === 'LOCAL') {
    lines.push({
      label: q.dayPriceApplied ? `${q.units} hours (capped at the day price)` : `${q.units} h × ${rupees(rates.hourPrice)}`,
      amount: q.base,
    });
    if (q.night) lines.push({ label: 'Night charge (10 pm – 6 am)', amount: q.night });
  } else {
    lines.push({ label: `${q.units} ${q.units === 1 ? 'day' : 'days'} × ${rupees(rates.outstationPrice)}`, amount: q.base });
    if (q.allowance) lines.push({ label: `Food and stay, ${q.units} × ${rupees(rates.allowance)}`, amount: q.allowance });
  }
  return lines;
}
