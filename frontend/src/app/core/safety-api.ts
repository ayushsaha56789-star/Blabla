import { HttpClient, HttpResponse } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { RideBrief } from './chat-api';
import { MemberCard } from './rides.models';

/** Mirrors SafetyDtos.java. */
export interface EmergencyContact {
  id: string;
  name: string;
  phone: string;
  createdAt: string;
}

export interface TripShare {
  /** "/trip/…" — add the website's own address. */
  path: string;
  expiresAt: string;
  liveAt: string | null;
}

export interface StopTime {
  position: number;
  name: string;
  state: string;
  meetingPoint: string | null;
  at: string;
}

export interface SharedTrip {
  sharerName: string;
  sharerIsDriver: boolean;
  ride: RideBrief;
  stops: StopTime[];
  driver: MemberCard;
  car: { make: string; model: string; color: string | null; plate: string | null } | null;
  live: { lat: number; lng: number; accuracy: number | null; at: string; near: string | null } | null;
  sosAt: string | null;
  expiresAt: string;
}

export interface SosResult {
  id: string;
  contactsNotified: number;
  sharePath: string | null;
  createdAt: string;
}

export interface GeoFix {
  lat: number;
  lng: number;
  accuracy: number | null;
}

@Injectable({ providedIn: 'root' })
export class SafetyApi {
  private readonly http = inject(HttpClient);

  contacts(): Promise<EmergencyContact[]> {
    return firstValueFrom(this.http.get<EmergencyContact[]>('/api/safety/contacts'));
  }

  addContact(name: string, phone: string): Promise<EmergencyContact> {
    return firstValueFrom(this.http.post<EmergencyContact>('/api/safety/contacts', { name, phone }));
  }

  removeContact(id: string): Promise<void> {
    return firstValueFrom(this.http.delete<void>(`/api/safety/contacts/${encodeURIComponent(id)}`));
  }

  share(rideId: string): Promise<TripShare> {
    return firstValueFrom(this.http.post<TripShare>(`/api/rides/${encodeURIComponent(rideId)}/share`, null));
  }

  /** The current link, or null when the member isn't sharing this ride. */
  async currentShare(rideId: string): Promise<TripShare | null> {
    const res: HttpResponse<TripShare> = await firstValueFrom(
      this.http.get<TripShare>(`/api/rides/${encodeURIComponent(rideId)}/share`, { observe: 'response' }),
    );
    return res.status === 204 ? null : res.body;
  }

  stopSharing(rideId: string): Promise<void> {
    return firstValueFrom(this.http.delete<void>(`/api/rides/${encodeURIComponent(rideId)}/share`));
  }

  sendLocation(rideId: string, fix: GeoFix): Promise<TripShare> {
    return firstValueFrom(this.http.post<TripShare>(`/api/rides/${encodeURIComponent(rideId)}/share/location`, fix));
  }

  sharedTrip(token: string): Promise<SharedTrip> {
    return firstValueFrom(this.http.get<SharedTrip>(`/api/shared-trips/${encodeURIComponent(token)}`));
  }

  sos(rideId: string | null, fix: GeoFix | null): Promise<SosResult> {
    return firstValueFrom(this.http.post<SosResult>('/api/sos', { rideId, ...(fix ?? {}) }));
  }
}

/** The phone's current position, or null if it isn't available within a few seconds. */
export function currentFix(timeoutMs = 5000): Promise<GeoFix | null> {
  if (!('geolocation' in navigator)) return Promise.resolve(null);
  return new Promise((resolve) => {
    navigator.geolocation.getCurrentPosition(
      (p) => resolve({ lat: p.coords.latitude, lng: p.coords.longitude, accuracy: Math.round(p.coords.accuracy) }),
      () => resolve(null),
      { enableHighAccuracy: true, timeout: timeoutMs, maximumAge: 30_000 },
    );
  });
}
