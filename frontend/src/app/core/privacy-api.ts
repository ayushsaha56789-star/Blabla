import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { firstValueFrom } from 'rxjs';

export type LeaveReason = 'NOT_USING' | 'NOT_ENOUGH_RIDES' | 'FOUND_ANOTHER_WAY' | 'SAFETY' | 'PRIVACY' | 'OTHER';

export const LEAVE_REASONS: Record<LeaveReason, string> = {
  NOT_USING: "I don't use it any more",
  NOT_ENOUGH_RIDES: 'Not enough rides on my routes',
  FOUND_ANOTHER_WAY: 'I travel another way now',
  SAFETY: "I didn't feel safe",
  PRIVACY: 'Privacy worries',
  OTHER: 'Something else',
};

/** Blank values mean "not appointed yet". */
export interface LegalContacts {
  company: string | null;
  grievanceOfficer: string | null;
  grievanceEmail: string | null;
  address: string | null;
}

@Injectable({ providedIn: 'root' })
export class PrivacyApi {
  private readonly http = inject(HttpClient);

  /** Everything SaathChalo holds about the member, as a JSON file. */
  exportData(): Promise<Blob> {
    return firstValueFrom(this.http.get('/api/privacy/export', { responseType: 'blob' }));
  }

  deleteAccount(reason: LeaveReason | null): Promise<{ deleteOn: string }> {
    return firstValueFrom(this.http.post<{ deleteOn: string }>('/api/privacy/delete-account', { reason }));
  }

  legalContacts(): Promise<LegalContacts> {
    return firstValueFrom(this.http.get<LegalContacts>('/api/legal/contacts'));
  }
}

/** Saves a file the app fetched (for example the data download). */
export function saveFile(blob: Blob, fileName: string): void {
  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = fileName;
  document.body.appendChild(a);
  a.click();
  a.remove();
  setTimeout(() => URL.revokeObjectURL(url), 1000);
}
