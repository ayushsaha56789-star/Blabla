import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { MemberCard, RideStatus } from './rides.models';

/** Mirrors ChatDtos.java. */
export interface RideBrief {
  id: string;
  driverId: string;
  fromName: string;
  toName: string;
  departureAt: string;
  arrivalAt: string;
  status: RideStatus;
  ladiesOnly: boolean;
}

export interface ChatMessage {
  id: string;
  mine: boolean;
  /** Null when staff removed the message after a report. */
  body: string | null;
  createdAt: string;
  hidden: boolean;
}

export interface Conversation {
  id: string;
  ride: RideBrief;
  other: MemberCard;
  otherIsDriver: boolean;
  passengerBooking: string | null;
  canSend: boolean;
  cannotSendReason: string | null;
  seenAt: string | null;
  messages: ChatMessage[];
}

export interface InboxItem {
  id: string;
  ride: RideBrief;
  other: MemberCard;
  otherIsDriver: boolean;
  lastMessage: string;
  lastFromMe: boolean;
  lastMessageAt: string;
  unread: number;
}

@Injectable({ providedIn: 'root' })
export class ChatApi {
  private readonly http = inject(HttpClient);

  /** Opens (or finds) the chat about a ride. Drivers pass the passenger's id. */
  open(rideId: string, passengerId?: string): Promise<Conversation> {
    return firstValueFrom(this.http.post<Conversation>('/api/chats', { rideId, passengerId: passengerId ?? null }));
  }

  inbox(): Promise<InboxItem[]> {
    return firstValueFrom(this.http.get<InboxItem[]>('/api/chats'));
  }

  unread(): Promise<{ conversations: number }> {
    return firstValueFrom(this.http.get<{ conversations: number }>('/api/chats/unread'));
  }

  conversation(id: string, after?: string): Promise<Conversation> {
    const params = after ? new HttpParams().set('after', after) : undefined;
    return firstValueFrom(this.http.get<Conversation>(`/api/chats/${encodeURIComponent(id)}`, { params }));
  }

  send(id: string, body: string): Promise<ChatMessage> {
    return firstValueFrom(this.http.post<ChatMessage>(`/api/chats/${encodeURIComponent(id)}/messages`, { body }));
  }
}
