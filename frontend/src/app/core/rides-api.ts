import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import {
  BookingView,
  BookRequest,
  Place,
  PublishRequest,
  Quote,
  RideDetails,
  SearchQuery,
  SearchResponse,
  Trips,
  TripsSummary,
  Vehicle,
  VehicleRequest,
} from './rides.models';

@Injectable({ providedIn: 'root' })
export class RidesApi {
  private readonly http = inject(HttpClient);

  // ----- Places -----

  searchPlaces(q: string, limit = 8): Promise<Place[]> {
    const params = new HttpParams().set('q', q).set('limit', limit);
    return firstValueFrom(this.http.get<Place[]>('/api/places', { params }));
  }

  place(id: number): Promise<Place> {
    return firstValueFrom(this.http.get<Place>(`/api/places/${encodeURIComponent(id)}`));
  }

  nearestPlace(lat: number, lng: number): Promise<Place> {
    const params = new HttpParams().set('lat', lat.toFixed(5)).set('lng', lng.toFixed(5));
    return firstValueFrom(this.http.get<Place>('/api/places/nearest', { params }));
  }

  placesAlong(fromId: number, toId: number): Promise<Place[]> {
    const params = new HttpParams().set('from', fromId).set('to', toId);
    return firstValueFrom(this.http.get<Place[]>('/api/places/along', { params }));
  }

  // ----- My cars -----

  myVehicles(): Promise<Vehicle[]> {
    return firstValueFrom(this.http.get<Vehicle[]>('/api/vehicles'));
  }

  addVehicle(body: VehicleRequest): Promise<Vehicle> {
    return firstValueFrom(this.http.post<Vehicle>('/api/vehicles', body));
  }

  updateVehicle(id: string, body: VehicleRequest): Promise<Vehicle> {
    return firstValueFrom(this.http.put<Vehicle>(`/api/vehicles/${encodeURIComponent(id)}`, body));
  }

  removeVehicle(id: string): Promise<void> {
    return firstValueFrom(this.http.delete<void>(`/api/vehicles/${encodeURIComponent(id)}`));
  }

  // ----- Rides -----

  search(q: SearchQuery): Promise<SearchResponse> {
    let params = new HttpParams();
    for (const [key, value] of Object.entries(q)) {
      if (value !== undefined && value !== null && value !== '') params = params.set(key, String(value));
    }
    return firstValueFrom(this.http.get<SearchResponse>('/api/rides/search', { params }));
  }

  quote(placeIds: number[]): Promise<Quote> {
    return firstValueFrom(this.http.post<Quote>('/api/rides/quote', { placeIds }));
  }

  publish(body: PublishRequest): Promise<RideDetails> {
    return firstValueFrom(this.http.post<RideDetails>('/api/rides', body));
  }

  ride(id: string): Promise<RideDetails> {
    return firstValueFrom(this.http.get<RideDetails>(`/api/rides/${encodeURIComponent(id)}`));
  }

  cancelRide(id: string, reason: string | null): Promise<RideDetails> {
    return firstValueFrom(this.http.post<RideDetails>(`/api/rides/${encodeURIComponent(id)}/cancel`, { reason }));
  }

  book(rideId: string, body: BookRequest): Promise<BookingView> {
    return firstValueFrom(this.http.post<BookingView>(`/api/rides/${encodeURIComponent(rideId)}/bookings`, body));
  }

  // ----- Bookings and trips -----

  acceptBooking(id: string): Promise<BookingView> {
    return firstValueFrom(this.http.post<BookingView>(`/api/bookings/${encodeURIComponent(id)}/accept`, null));
  }

  declineBooking(id: string): Promise<BookingView> {
    return firstValueFrom(this.http.post<BookingView>(`/api/bookings/${encodeURIComponent(id)}/decline`, null));
  }

  cancelBooking(id: string): Promise<BookingView> {
    return firstValueFrom(this.http.post<BookingView>(`/api/bookings/${encodeURIComponent(id)}/cancel`, null));
  }

  trips(when: 'upcoming' | 'past'): Promise<Trips> {
    return firstValueFrom(this.http.get<Trips>('/api/trips', { params: new HttpParams().set('when', when) }));
  }

  tripsSummary(): Promise<TripsSummary> {
    return firstValueFrom(this.http.get<TripsSummary>('/api/trips/summary'));
  }
}
