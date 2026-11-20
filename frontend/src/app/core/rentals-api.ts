import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { CheckoutView } from './payments-api';
import { MemberCard } from './rides.models';
import { rupees } from './rides-format';

/** Car rentals. Money in rupees. */
export type RentalMode = 'SELF_DRIVE' | 'WITH_DRIVER';
export type FuelPolicy = 'SAME_LEVEL' | 'INCLUDED';
export type Fuel = 'EMPTY' | 'QUARTER' | 'HALF' | 'THREE_QUARTERS' | 'FULL';
export type DocumentKind = 'RC' | 'INSURANCE' | 'PERMIT';
export type ListingStatus = 'DRAFT' | 'IN_REVIEW' | 'LIVE' | 'PAUSED' | 'REJECTED' | 'REMOVED';
export type RentalStatus = 'REQUESTED' | 'ACCEPTED' | 'PAID' | 'PICKED_UP' | 'RETURNED' | 'DISPUTED' | 'COMPLETED'
  | 'DECLINED' | 'EXPIRED' | 'CANCELLED';

export const FUEL_LABELS: Record<Fuel, string> = {
  EMPTY: 'Nearly empty', QUARTER: '¼ tank', HALF: '½ tank', THREE_QUARTERS: '¾ tank', FULL: 'Full',
};

export const DOCUMENT_LABELS: Record<DocumentKind, string> = {
  RC: 'Registration (RC)', INSURANCE: 'Insurance', PERMIT: 'Permit (if your car has one)',
};

export interface CarSummary {
  vehicleId: string | null;
  name: string;
  color: string | null;
  seats: number;
}

export interface ListingCard {
  id: string;
  car: CarSummary;
  photoUrl: string | null;
  placeName: string;
  area: string;
  distanceKm: number | null;
  selfDrive: boolean;
  withDriver: boolean;
  dayPrice: number;
  hourPrice: number | null;
  deposit: number;
  kmPerDay: number;
  verified: boolean;
  instant: boolean;
  owner: MemberCard;
  priceForPeriod: number | null;
}

export interface Period {
  from: string;
  to: string;
}

export interface ListingDetail {
  id: string;
  car: CarSummary;
  photoUrls: string[];
  placeId: number;
  placeName: string;
  state: string | null;
  area: string;
  lat: number;
  lng: number;
  selfDrive: boolean;
  withDriver: boolean;
  dayPrice: number;
  hourPrice: number | null;
  deposit: number;
  kmPerDay: number;
  extraKmPrice: number;
  fuelPolicy: FuelPolicy;
  minAge: number;
  description: string | null;
  rules: string | null;
  instant: boolean;
  verified: boolean;
  status: ListingStatus;
  owner: MemberCard;
  unavailable: Period[];
  mine: boolean;
}

export interface MyListing {
  listing: ListingDetail;
  pickupAddress: string | null;
  reviewNote: string | null;
  photos: { id: string; url: string }[];
  documents: { id: string; kind: DocumentKind; uploadedAt: string }[];
  blocks: { id: string; from: string; to: string; note: string | null }[];
  openRequests: number;
}

export interface ListingRequest {
  vehicleId: string;
  selfDrive: boolean;
  withDriver: boolean;
  dayPrice: number;
  hourPrice: number | null;
  deposit: number;
  kmPerDay: number;
  extraKmPrice: number;
  fuelPolicy: FuelPolicy;
  minAge: number;
  placeId: number;
  area: string;
  pickupAddress: string | null;
  description: string | null;
  rules: string | null;
  instant: boolean;
}

export interface Quote {
  available: boolean;
  reason: string | null;
  rent: number;
  deposit: number;
  kmIncluded: number;
  extraKmPrice: number;
  hours: number;
  hourly: boolean;
}

export interface CheckView {
  stage: 'PICKUP' | 'RETURN';
  byRole: 'OWNER' | 'RENTER';
  odometer: number;
  fuel: Fuel;
  notes: string | null;
  photoUrls: string[];
  at: string;
}

export interface RentalView {
  id: string;
  viewer: 'RENTER' | 'OWNER' | 'STAFF';
  listing: ListingCard;
  mode: RentalMode;
  from: string;
  to: string;
  rent: number;
  deposit: number;
  kmIncluded: number;
  extraKmPrice: number;
  payOnline: boolean;
  payBy: string | null;
  status: RentalStatus;
  renter: MemberCard;
  owner: MemberCard;
  otherPhone: string | null;
  pickupAddress: string | null;
  message: string | null;
  checks: CheckView[];
  kmDriven: number | null;
  extraKmCharge: number;
  extraCharges: number;
  extraNote: string | null;
  disputeNote: string | null;
  resolution: string | null;
  createdAt: string;
  decidedAt: string | null;
  pickedUpAt: string | null;
  returnedAt: string | null;
  respondBy: string | null;
  completedAt: string | null;
  cancelledAt: string | null;
  cancelledBy: string | null;
}

/** For staff: a listing with its documents (photos fetched from the console API). */
export interface AdminListingRow {
  listing: ListingDetail;
  pickupAddress: string | null;
  reviewNote: string | null;
  documents: { id: string; kind: DocumentKind; uploadedAt: string }[];
  updatedAt: string;
}

export interface CheckForm {
  odometer: number;
  fuel: Fuel;
  notes: string;
  photos: Blob[];
  otherCharges?: number;
  chargesNote?: string;
}

@Injectable({ providedIn: 'root' })
export class RentalsApi {
  private readonly http = inject(HttpClient);

  search(placeId: number, from: string | null, to: string | null, mode: RentalMode | null): Promise<ListingCard[]> {
    let params = new HttpParams().set('placeId', placeId);
    if (from && to) params = params.set('from', from).set('to', to);
    if (mode) params = params.set('mode', mode);
    return firstValueFrom(this.http.get<ListingCard[]>('/api/rentals/search', { params }));
  }

  listing(id: string): Promise<ListingDetail> {
    return firstValueFrom(this.http.get<ListingDetail>(`/api/rentals/listings/${encodeURIComponent(id)}`));
  }

  quote(id: string, from: string, to: string, mode: RentalMode): Promise<Quote> {
    const params = new HttpParams().set('from', from).set('to', to).set('mode', mode);
    return firstValueFrom(this.http.get<Quote>(`/api/rentals/listings/${encodeURIComponent(id)}/quote`, { params }));
  }

  myListings(): Promise<MyListing[]> {
    return firstValueFrom(this.http.get<MyListing[]>('/api/rentals/listings/mine'));
  }

  myListing(id: string): Promise<MyListing> {
    return firstValueFrom(this.http.get<MyListing>(`/api/rentals/listings/${encodeURIComponent(id)}/manage`));
  }

  createListing(body: ListingRequest): Promise<MyListing> {
    return firstValueFrom(this.http.post<MyListing>('/api/rentals/listings', body));
  }

  updateListing(id: string, body: ListingRequest): Promise<MyListing> {
    return firstValueFrom(this.http.put<MyListing>(`/api/rentals/listings/${encodeURIComponent(id)}`, body));
  }

  addPhoto(id: string, photo: Blob): Promise<MyListing> {
    const form = new FormData();
    form.append('photo', photo, 'car.jpg');
    return firstValueFrom(this.http.post<MyListing>(`/api/rentals/listings/${encodeURIComponent(id)}/photos`, form));
  }

  removePhoto(id: string, photoId: string): Promise<MyListing> {
    return firstValueFrom(this.http.delete<MyListing>(`/api/rentals/listings/${encodeURIComponent(id)}/photos/${encodeURIComponent(photoId)}`));
  }

  addDocument(id: string, kind: DocumentKind, file: Blob): Promise<MyListing> {
    const form = new FormData();
    form.append('kind', kind);
    form.append('file', file, 'document.jpg');
    return firstValueFrom(this.http.post<MyListing>(`/api/rentals/listings/${encodeURIComponent(id)}/documents`, form));
  }

  listingAction(id: string, action: 'submit' | 'pause' | 'resume'): Promise<MyListing> {
    return firstValueFrom(this.http.post<MyListing>(`/api/rentals/listings/${encodeURIComponent(id)}/${action}`, null));
  }

  removeListing(id: string): Promise<void> {
    return firstValueFrom(this.http.delete<void>(`/api/rentals/listings/${encodeURIComponent(id)}`));
  }

  addBlock(id: string, from: string, to: string, note: string | null): Promise<MyListing> {
    return firstValueFrom(this.http.post<MyListing>(`/api/rentals/listings/${encodeURIComponent(id)}/blocks`, { from, to, note }));
  }

  removeBlock(id: string, blockId: string): Promise<MyListing> {
    return firstValueFrom(this.http.delete<MyListing>(`/api/rentals/listings/${encodeURIComponent(id)}/blocks/${encodeURIComponent(blockId)}`));
  }

  rent(listingId: string, mode: RentalMode, from: string, to: string, message: string | null): Promise<RentalView> {
    return firstValueFrom(this.http.post<RentalView>('/api/rentals/bookings', { listingId, mode, from, to, message }));
  }

  myRentals(): Promise<{ renting: RentalView[]; lending: RentalView[] }> {
    return firstValueFrom(this.http.get<{ renting: RentalView[]; lending: RentalView[] }>('/api/rentals/bookings'));
  }

  rental(id: string): Promise<RentalView> {
    return firstValueFrom(this.http.get<RentalView>(`/api/rentals/bookings/${encodeURIComponent(id)}`));
  }

  bookingAction(id: string, action: 'accept' | 'decline' | 'cancel' | 'accept-return'): Promise<RentalView> {
    return firstValueFrom(this.http.post<RentalView>(`/api/rentals/bookings/${encodeURIComponent(id)}/${action}`, null));
  }

  pay(id: string, useCredits: boolean): Promise<CheckoutView> {
    return firstValueFrom(this.http.post<CheckoutView>(`/api/rentals/bookings/${encodeURIComponent(id)}/pay`, { useCredits }));
  }

  dispute(id: string, note: string): Promise<RentalView> {
    return firstValueFrom(this.http.post<RentalView>(`/api/rentals/bookings/${encodeURIComponent(id)}/dispute`, { note }));
  }

  check(id: string, stage: 'pickup' | 'return', f: CheckForm): Promise<RentalView> {
    const form = new FormData();
    form.append('odometer', String(f.odometer));
    form.append('fuel', f.fuel);
    if (f.notes.trim()) form.append('notes', f.notes.trim());
    if (f.otherCharges) form.append('otherCharges', String(f.otherCharges));
    if (f.chargesNote?.trim()) form.append('chargesNote', f.chargesNote.trim());
    f.photos.forEach((p, i) => form.append('photos', p, `photo-${i + 1}.jpg`));
    return firstValueFrom(this.http.post<RentalView>(`/api/rentals/bookings/${encodeURIComponent(id)}/${stage}`, form));
  }

  /** Private photos need the login token, so they're fetched rather than linked. */
  photo(url: string): Promise<Blob> {
    return firstValueFrom(this.http.get(url, { responseType: 'blob' }));
  }
}

/** How a rental's state reads, from the viewer's side. */
export function rentalStatus(r: Pick<RentalView, 'status' | 'payOnline' | 'viewer' | 'cancelledBy'>): { text: string; tone: string } {
  const owner = r.viewer === 'OWNER';
  switch (r.status) {
    case 'REQUESTED': return { text: owner ? 'Waiting for your answer' : 'Waiting for the owner', tone: 'warning' };
    case 'ACCEPTED': return r.payOnline ? { text: owner ? 'Waiting for payment' : 'Accepted: pay to confirm', tone: 'warning' }
      : { text: 'Confirmed: pay at pickup', tone: 'success' };
    case 'PAID': return { text: 'Confirmed and paid', tone: 'success' };
    case 'PICKED_UP': return { text: 'On the road', tone: 'success' };
    case 'RETURNED': return { text: owner ? 'Returned: waiting for the renter' : 'Returned: check the charges', tone: 'warning' };
    case 'DISPUTED': return { text: 'Being checked by SaathChalo', tone: 'warning' };
    case 'COMPLETED': return { text: 'Completed', tone: 'muted' };
    case 'DECLINED': return { text: 'Declined', tone: 'danger' };
    case 'EXPIRED': return { text: 'Lapsed', tone: 'muted' };
    case 'CANCELLED':
      return { text: r.cancelledBy === 'OWNER' ? 'Cancelled by the owner' : r.cancelledBy === 'STAFF' ? 'Cancelled by SaathChalo'
        : r.cancelledBy === 'RENTER' ? (owner ? 'Cancelled by the renter' : 'You cancelled') : 'Cancelled', tone: 'muted' };
  }
}

type MoneyFacts = Pick<RentalView, 'deposit' | 'extraCharges' | 'extraKmCharge' | 'extraNote' | 'payOnline' | 'viewer'>;

/** The owner's charges after the return, as the renter reads them. */
export function chargesLine(r: MoneyFacts): string {
  if (!r.extraCharges) return 'No charges: the whole deposit comes back.';
  let line = `The owner keeps ${rupees(r.extraCharges)} from the deposit`;
  if (r.extraKmCharge) line += ` (${rupees(r.extraKmCharge)} for extra km)`;
  if (r.extraNote) line += `: ${r.extraNote}`;
  return line + '.';
}

/** What happens to the deposit once a rental is complete. Cash deposits go back by hand, online ones as a refund. */
export function completedLine(r: MoneyFacts): string {
  if (!r.deposit) return 'Completed.';
  const owner = r.viewer === 'OWNER';
  const kept = Math.min(r.extraCharges, r.deposit);
  const back = r.deposit - kept;
  if (r.payOnline) {
    if (!kept) return owner ? 'Completed. The deposit went back to the renter.' : 'Completed. Your deposit is coming back to you.';
    return owner ? `Completed. You keep ${rupees(kept)} from the deposit; ${rupees(back)} went back to the renter.`
      : `Completed. The owner keeps ${rupees(kept)} from the deposit; ${rupees(back)} comes back to you.`;
  }
  if (!kept) return owner ? `Completed. Please give the renter's ${rupees(r.deposit)} deposit back.`
    : `Completed. The owner gives your ${rupees(r.deposit)} deposit back.`;
  return owner ? `Completed. Keep ${rupees(kept)} from the deposit and give ${rupees(back)} back to the renter.`
    : `Completed. The owner keeps ${rupees(kept)} from the deposit and gives ${rupees(back)} back to you.`;
}

/** "2026-11-21T09:30" for a datetime-local input, in the browser's time. */
export function toLocalInput(d: Date): string {
  const pad = (n: number) => String(n).padStart(2, '0');
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}`;
}

/** A datetime-local value as an ISO instant, or null when empty or invalid. */
export function fromLocalInput(value: string): string | null {
  if (!value) return null;
  const d = new Date(value);
  return Number.isNaN(d.getTime()) ? null : d.toISOString();
}
