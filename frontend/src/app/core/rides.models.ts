/** Carpooling API shapes — mirrors RideDtos.java, VehicleDtos.java and geo/Place.java in the backend. */
import { PublicProfile } from './api.models';

export interface Place {
  id: number;
  name: string;
  state: string;
  lat: number;
  lng: number;
  population: number;
}

export interface Vehicle {
  id: string;
  make: string;
  model: string;
  color: string | null;
  seats: number;
  /** Full registration number, e.g. "MH 12 AB 1234" (only ever sent to the owner). */
  plate: string;
  createdAt: string;
}

export interface VehicleRequest {
  make: string;
  model: string;
  color: string | null;
  seats: number;
  plate: string;
}

export interface Quote {
  distanceKm: number;
  durationMin: number;
  suggestedPrice: number;
  minPrice: number;
  maxPrice: number;
  kmFromStart: number[];
  minutesFromStart: number[];
}

export type RideStatus = 'SCHEDULED' | 'CANCELLED' | 'COMPLETED';
export type BookingStatus = 'AWAITING_PAYMENT' | 'REQUESTED' | 'CONFIRMED' | 'DECLINED' | 'CANCELLED' | 'EXPIRED' | 'COMPLETED';
export type Viewer = 'DRIVER' | 'PASSENGER' | 'VISITOR' | 'STAFF';

export interface MemberCard {
  id: string | null;
  displayName: string;
  avatarUrl: string | null;
  phoneVerified: boolean;
  idVerified: boolean;
  memberSince: string | null;
  /** Average stars from trip reviews; null until someone reviews them. */
  rating: number | null;
  reviewCount: number;
}

export interface StopView {
  position: number;
  placeId: number;
  name: string;
  state: string;
  meetingPoint: string | null;
  lat: number;
  lng: number;
  kmFromStart: number;
  /** When the car is expected there. */
  at: string;
}

export interface CarView {
  make: string;
  model: string;
  color: string | null;
  /** Only for the driver and passengers with a confirmed seat. */
  plate: string | null;
}

export interface DriverView {
  profile: PublicProfile;
  /** Only for passengers with a confirmed seat. */
  phone: string | null;
  ridesCompleted: number;
}

export interface PassengerView {
  bookingId: string;
  member: MemberCard;
  status: BookingStatus;
  fromPosition: number;
  toPosition: number;
  seats: number;
  pricePerSeat: number;
  message: string | null;
  phone: string | null;
  requestedAt: string;
  /** PASSENGER, DRIVER, STAFF or SYSTEM, for cancelled and expired bookings. */
  cancelledBy: string | null;
}

export interface BookingView {
  id: string;
  rideId: string;
  status: BookingStatus;
  fromPosition: number;
  toPosition: number;
  fromName: string;
  toName: string;
  pickupAt: string;
  dropAt: string;
  seats: number;
  pricePerSeat: number;
  total: number;
  message: string | null;
  createdAt: string;
  decidedAt: string | null;
  expiresAt: string | null;
  cancelledBy: string | null;
}

export interface RideDetails {
  id: string;
  status: RideStatus;
  departureAt: string;
  arrivalAt: string;
  distanceKm: number;
  durationMin: number;
  pricePerSeat: number;
  seatsTotal: number;
  /** Free seats between stop i and stop i + 1. */
  seatsLeftByLeg: number[];
  /** prices[i][j]: price per seat from stop i to stop j. */
  prices: number[][];
  ladiesOnly: boolean;
  instantBooking: boolean;
  notes: string | null;
  cancelledAt: string | null;
  cancelledBy: string | null;
  cancelReason: string | null;
  stops: StopView[];
  driver: DriverView;
  car: CarView | null;
  passengers: PassengerView[];
  viewer: Viewer;
  myBooking: BookingView | null;
  bookable: boolean;
}

export interface SearchResult {
  rideId: string;
  fromPosition: number;
  toPosition: number;
  pickup: StopView;
  drop: StopView;
  pickupDistanceKm: number;
  dropDistanceKm: number;
  price: number;
  seatsLeft: number;
  instantBooking: boolean;
  ladiesOnly: boolean;
  driver: MemberCard;
  car: string | null;
  rideFrom: string;
  rideTo: string;
  stopCount: number;
  yours: boolean;
  yourBooking: BookingStatus | null;
}

export interface DateCount {
  date: string;
  rides: number;
}

export interface SearchResponse {
  from: Place;
  to: Place;
  date: string;
  seats: number;
  rides: SearchResult[];
  otherDates: DateCount[];
}

export interface RideSummary {
  id: string;
  status: RideStatus;
  departureAt: string;
  arrivalAt: string;
  fromName: string;
  toName: string;
  via: string[];
  pricePerSeat: number;
  seatsTotal: number;
  ladiesOnly: boolean;
  instantBooking: boolean;
}

export interface DrivingTrip {
  ride: RideSummary;
  seatsBooked: number;
  pendingRequests: number;
}

export interface RidingTrip {
  booking: BookingView;
  ride: RideSummary;
  driver: MemberCard;
}

export interface Trips {
  driving: DrivingTrip[];
  riding: RidingTrip[];
}

export interface TripsSummary {
  pendingRequests: number;
  upcomingTrips: number;
}

export interface PublishRequest {
  vehicleId: string;
  stops: { placeId: number; meetingPoint: string | null }[];
  date: string;
  time: string;
  seats: number;
  pricePerSeat: number;
  ladiesOnly: boolean;
  instantBooking: boolean;
  notes: string | null;
}

export interface BookRequest {
  fromPosition: number;
  toPosition: number;
  seats: number;
  message: string | null;
}

export interface SearchQuery {
  fromId?: number;
  from?: string;
  toId?: number;
  to?: string;
  date?: string;
  seats?: number;
}
