import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { RideBrief } from './chat-api';
import { MemberCard } from './rides.models';

/** Mirrors ReviewDtos.java. */
export interface Review {
  id: string;
  author: MemberCard;
  /** What the reviewed member was on that trip. */
  subjectRole: 'DRIVER' | 'PASSENGER';
  rating: number;
  comment: string | null;
  fromName: string | null;
  toName: string | null;
  createdAt: string;
}

export interface PendingReview {
  ride: RideBrief;
  subject: MemberCard;
  subjectRole: 'DRIVER' | 'PASSENGER';
}

export interface ReviewPage {
  average: number | null;
  count: number;
  items: Review[];
  total: number;
  page: number;
  size: number;
}

@Injectable({ providedIn: 'root' })
export class ReviewsApi {
  private readonly http = inject(HttpClient);

  pending(): Promise<PendingReview[]> {
    return firstValueFrom(this.http.get<PendingReview[]>('/api/reviews/pending'));
  }

  write(rideId: string, subjectId: string, rating: number, comment: string | null): Promise<Review> {
    return firstValueFrom(this.http.post<Review>('/api/reviews', { rideId, subjectId, rating, comment }));
  }

  about(memberId: string, page = 0, size = 10): Promise<ReviewPage> {
    const params = new HttpParams().set('page', page).set('size', size);
    return firstValueFrom(this.http.get<ReviewPage>(`/api/users/${encodeURIComponent(memberId)}/reviews`, { params }));
  }
}
