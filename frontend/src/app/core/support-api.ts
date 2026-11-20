import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { firstValueFrom } from 'rxjs';

export type SupportCategory = 'ACCOUNT' | 'BOOKING' | 'OFFERING' | 'PAYMENT' | 'SAFETY' | 'PRIVACY' | 'BUG' | 'GRIEVANCE' | 'OTHER';

export const CATEGORIES: Record<SupportCategory, string> = {
  BOOKING: 'A ride I booked',
  OFFERING: 'A ride I offered',
  PAYMENT: 'Money or prices',
  SAFETY: 'Safety',
  ACCOUNT: 'My account or login',
  PRIVACY: 'My data and privacy',
  BUG: 'Something is not working',
  GRIEVANCE: 'A formal complaint (grievance)',
  OTHER: 'Something else',
};

export interface SupportMessage {
  id: string;
  fromStaff: boolean;
  author: string;
  body: string;
  createdAt: string;
}

export interface TicketSummary {
  id: string;
  category: SupportCategory;
  subject: string;
  status: 'OPEN' | 'ANSWERED' | 'CLOSED';
  /** Support replied and the member hasn't opened the request since. */
  newReply?: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface Ticket extends TicketSummary {
  rideId: string | null;
  firstResponseAt: string | null;
  messages: SupportMessage[];
}

@Injectable({ providedIn: 'root' })
export class SupportApi {
  private readonly http = inject(HttpClient);

  create(category: SupportCategory, subject: string, body: string, rideId: string | null): Promise<Ticket> {
    return firstValueFrom(this.http.post<Ticket>('/api/support/tickets', { category, subject, body, rideId }));
  }

  mine(): Promise<TicketSummary[]> {
    return firstValueFrom(this.http.get<TicketSummary[]>('/api/support/tickets'));
  }

  /** How many requests have a reply the member hasn't read. */
  newReplies(): Promise<{ count: number }> {
    return firstValueFrom(this.http.get<{ count: number }>('/api/support/tickets/answered'));
  }

  get(id: string): Promise<Ticket> {
    return firstValueFrom(this.http.get<Ticket>(`/api/support/tickets/${encodeURIComponent(id)}`));
  }

  reply(id: string, body: string): Promise<Ticket> {
    return firstValueFrom(this.http.post<Ticket>(`/api/support/tickets/${encodeURIComponent(id)}/messages`, { body }));
  }

  close(id: string): Promise<Ticket> {
    return firstValueFrom(this.http.post<Ticket>(`/api/support/tickets/${encodeURIComponent(id)}/close`, null));
  }
}

/** How a request's state reads to the member. */
export function ticketStatus(status: TicketSummary['status']): { text: string; tone: string } {
  return status === 'OPEN' ? { text: 'Waiting for us', tone: 'warning' }
    : status === 'ANSWERED' ? { text: 'We replied', tone: 'success' }
    : { text: 'Closed', tone: 'muted' };
}
