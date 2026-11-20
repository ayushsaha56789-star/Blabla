import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { MemberCard } from './rides.models';

export type ReportTarget = 'MEMBER' | 'RIDE' | 'MESSAGE' | 'REVIEW';
export type ReportReason = 'SAFETY' | 'HARASSMENT' | 'FAKE_PROFILE' | 'NO_SHOW' | 'SPAM' | 'INAPPROPRIATE' | 'OTHER';

export const REASONS: Record<ReportReason, string> = {
  SAFETY: 'I feel unsafe',
  HARASSMENT: 'Harassment or threats',
  FAKE_PROFILE: 'Fake profile, or pretending to be someone',
  NO_SHOW: "Didn't turn up for the trip",
  SPAM: 'Spam or a scam',
  INAPPROPRIATE: 'Rude or inappropriate',
  OTHER: 'Something else',
};

/** Which reasons make sense for each kind of report. */
export const REASONS_FOR: Record<ReportTarget, ReportReason[]> = {
  MEMBER: ['SAFETY', 'HARASSMENT', 'FAKE_PROFILE', 'NO_SHOW', 'SPAM', 'OTHER'],
  RIDE: ['SAFETY', 'SPAM', 'INAPPROPRIATE', 'OTHER'],
  MESSAGE: ['HARASSMENT', 'SAFETY', 'SPAM', 'INAPPROPRIATE', 'OTHER'],
  REVIEW: ['INAPPROPRIATE', 'HARASSMENT', 'SPAM', 'OTHER'],
};

@Injectable({ providedIn: 'root' })
export class ModerationApi {
  private readonly http = inject(HttpClient);

  report(targetType: ReportTarget, targetId: string, reason: ReportReason, details: string | null): Promise<{ id: string }> {
    return firstValueFrom(this.http.post<{ id: string }>('/api/reports', { targetType, targetId, reason, details }));
  }

  block(memberId: string): Promise<void> {
    return firstValueFrom(this.http.post<void>(`/api/users/${encodeURIComponent(memberId)}/block`, null));
  }

  unblock(memberId: string): Promise<void> {
    return firstValueFrom(this.http.delete<void>(`/api/users/${encodeURIComponent(memberId)}/block`));
  }

  myBlocks(): Promise<MemberCard[]> {
    return firstValueFrom(this.http.get<MemberCard[]>('/api/users/me/blocks'));
  }
}
