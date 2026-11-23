import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { MemberCard } from './rides.models';

/** Members' promotions, always shown as "Sponsored". */
export type PromoCategory = 'FOOD' | 'SHOP' | 'SERVICES' | 'EDUCATION' | 'EVENTS' | 'TRAVEL' | 'FITNESS' | 'CREATOR' | 'TECH' | 'OTHER';
export type PromoSlot = 'DISCOVER' | 'SEARCH' | 'GAMES';
export type PromoStatus = 'DRAFT' | 'IN_REVIEW' | 'APPROVED' | 'REJECTED' | 'STOPPED' | 'TAKEN_DOWN';

export const CATEGORY_LABELS: Record<PromoCategory, string> = {
  FOOD: 'Food & drink (no alcohol)', SHOP: 'Shop', SERVICES: 'Services', EDUCATION: 'Classes & courses', EVENTS: 'Events',
  TRAVEL: 'Travel & stays', FITNESS: 'Fitness & wellness', CREATOR: 'Creator', TECH: 'Apps & tech', OTHER: 'Something else',
};

export interface PromoCard {
  id: string;
  business: string;
  title: string;
  body: string;
  linkUrl: string | null;
  category: PromoCategory;
  imageUrl: string | null;
}

export interface MyPromotion {
  card: PromoCard;
  allIndia: boolean;
  cities: { placeId: number; name: string }[];
  startsOn: string;
  endsOn: string;
  status: PromoStatus;
  live: boolean;
  reviewNote: string | null;
  views: number;
  clicks: number;
  pricePaise: number;
  updatedAt: string;
}

export interface PromotionRequest {
  business: string;
  title: string;
  body: string;
  linkUrl: string | null;
  category: PromoCategory;
  allIndia: boolean;
  placeIds: number[];
  startsOn: string;
  endsOn: string;
}

export interface AdminPromotionRow {
  promotion: MyPromotion;
  owner: MemberCard;
  flags: string | null;
}

@Injectable({ providedIn: 'root' })
export class PromotionsApi {
  private readonly http = inject(HttpClient);

  /** Sponsored cards for a slot; each one returned counts as seen. */
  feed(slot: PromoSlot, placeIds: number[] = [], limit = 1): Promise<PromoCard[]> {
    let params = new HttpParams().set('slot', slot).set('limit', limit);
    if (placeIds.length) params = params.set('placeIds', placeIds.join(','));
    return firstValueFrom(this.http.get<PromoCard[]>('/api/promotions/feed', { params }));
  }

  click(id: string): Promise<void> {
    return firstValueFrom(this.http.post<void>(`/api/promotions/${encodeURIComponent(id)}/click`, null));
  }

  options(): Promise<{ categories: PromoCategory[]; free: boolean; freeUntil: string | null; maxDays: number }> {
    return firstValueFrom(this.http.get<{ categories: PromoCategory[]; free: boolean; freeUntil: string | null; maxDays: number }>(
      '/api/promotions/options'));
  }

  mine(): Promise<MyPromotion[]> {
    return firstValueFrom(this.http.get<MyPromotion[]>('/api/promotions/mine'));
  }

  save(id: string | null, body: PromotionRequest): Promise<MyPromotion> {
    return firstValueFrom(id ? this.http.put<MyPromotion>(`/api/promotions/${encodeURIComponent(id)}`, body)
      : this.http.post<MyPromotion>('/api/promotions', body));
  }

  image(id: string, file: Blob): Promise<MyPromotion> {
    const form = new FormData();
    form.append('file', file, 'picture.jpg');
    return firstValueFrom(this.http.post<MyPromotion>(`/api/promotions/${encodeURIComponent(id)}/image`, form));
  }

  action(id: string, action: 'submit' | 'stop'): Promise<MyPromotion> {
    return firstValueFrom(this.http.post<MyPromotion>(`/api/promotions/${encodeURIComponent(id)}/${action}`, null));
  }

  remove(id: string): Promise<void> {
    return firstValueFrom(this.http.delete<void>(`/api/promotions/${encodeURIComponent(id)}`));
  }
}

/** How a promotion's state reads to its owner. */
export function promoStatus(p: Pick<MyPromotion, 'status' | 'live' | 'endsOn'>): { text: string; tone: string } {
  switch (p.status) {
    case 'DRAFT': return { text: 'Draft', tone: 'muted' };
    case 'IN_REVIEW': return { text: 'Being checked', tone: 'warning' };
    case 'REJECTED': return { text: 'Needs changes', tone: 'danger' };
    case 'STOPPED': return { text: 'Stopped', tone: 'muted' };
    case 'TAKEN_DOWN': return { text: 'Taken down', tone: 'danger' };
    case 'APPROVED':
      if (p.live) return { text: 'Live', tone: 'success' };
      return Date.parse(p.endsOn) < Date.now() ? { text: 'Ended', tone: 'muted' } : { text: 'Approved: starts soon', tone: 'success' };
  }
}

/** Clicks per hundred views, rounded. */
/** "1 view · 3 clicks" */
export function reach(views: number, clicks: number): string {
  return `${views} ${views === 1 ? 'view' : 'views'} · ${clicks} ${clicks === 1 ? 'click' : 'clicks'}`;
}

export function clickRate(views: number, clicks: number): string {
  return views ? `${Math.round((clicks / views) * 1000) / 10}%` : '–';
}
