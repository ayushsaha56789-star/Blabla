import { BookingStatus } from './rides.models';

const RUPEES = new Intl.NumberFormat('en-IN', { maximumFractionDigits: 0 });

/** 1040 → "₹1,040"; 125000 → "₹1,25,000" (Indian digit grouping). */
export function rupees(amount: number): string {
  return `₹${RUPEES.format(amount)}`;
}

/** 345 → "5 h 45 min", 180 → "3 h", 40 → "40 min". */
export function duration(minutes: number): string {
  const h = Math.floor(minutes / 60);
  const m = Math.round(minutes % 60);
  if (h === 0) return `${m} min`;
  return m === 0 ? `${h} h` : `${h} h ${m} min`;
}

/** Free seats for a stretch of a ride: the fewest on any part of it. */
export function seatsLeftFor(seatsLeftByLeg: readonly number[], from: number, to: number): number {
  let min = Number.POSITIVE_INFINITY;
  for (let leg = from; leg < to; leg++) min = Math.min(min, seatsLeftByLeg[leg] ?? 0);
  return Number.isFinite(min) ? min : 0;
}

/**
 * Preview of what part of a route will cost while a driver is setting the price. Same rule as the server: the
 * whole-route price shared by distance, rounded to ₹10, never below ₹10 or above the whole-route price.
 */
export function stretchPrice(fullPrice: number, kmFromStart: readonly number[], from: number, to: number): number {
  const last = kmFromStart.length - 1;
  const total = kmFromStart[last];
  if ((from === 0 && to === last) || !total) return fullPrice;
  const price = Math.round((fullPrice * (kmFromStart[to] - kmFromStart[from])) / total / 10) * 10;
  return Math.min(fullPrice, Math.max(Math.min(10, fullPrice), price));
}

export interface StatusLabel {
  text: string;
  tone: 'success' | 'warning' | 'danger' | 'muted';
}

/** How a booking's state reads to the passenger. */
export function bookingStatus(status: BookingStatus, cancelledBy: string | null = null): StatusLabel {
  switch (status) {
    case 'AWAITING_PAYMENT':
      return { text: 'Waiting for payment', tone: 'warning' };
    case 'CONFIRMED':
      return { text: 'Seat confirmed', tone: 'success' };
    case 'REQUESTED':
      return { text: 'Waiting for the driver', tone: 'warning' };
    case 'DECLINED':
      return { text: 'Declined by the driver', tone: 'danger' };
    case 'EXPIRED':
      return { text: 'Request expired', tone: 'muted' };
    case 'COMPLETED':
      return { text: 'Completed', tone: 'muted' };
    case 'CANCELLED':
      if (cancelledBy === 'DRIVER') return { text: 'Ride cancelled by the driver', tone: 'danger' };
      if (cancelledBy === 'STAFF') return { text: 'Ride cancelled by SaathChalo', tone: 'danger' };
      return { text: 'You cancelled', tone: 'muted' };
  }
}

/** "+919876543210" → a WhatsApp chat link. */
export function whatsappLink(phone: string): string {
  return `https://wa.me/${phone.replace(/\D/g, '')}`;
}

/** Adds days to a YYYY-MM-DD date. */
export function addDays(iso: string, days: number): string {
  const [y, m, d] = iso.split('-').map(Number);
  const date = new Date(Date.UTC(y, m - 1, d + days));
  return date.toISOString().slice(0, 10);
}

/** "2026-11-21" → a Date at noon UTC, safe to format as that calendar day in any time zone. */
export function dayOf(iso: string): Date {
  return new Date(`${iso}T12:00:00Z`);
}
