import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { firstValueFrom } from 'rxjs';

export type DocumentType = 'DRIVING_LICENCE' | 'AADHAAR' | 'PAN' | 'PASSPORT' | 'VOTER_ID';

export const DOCUMENTS: Record<DocumentType, string> = {
  DRIVING_LICENCE: 'Driving licence',
  AADHAAR: 'Aadhaar (masked)',
  PAN: 'PAN card',
  PASSPORT: 'Passport',
  VOTER_ID: 'Voter ID',
};

export type RejectReason = 'UNREADABLE' | 'NAME_MISMATCH' | 'NOT_ACCEPTED' | 'EXPIRED' | 'AADHAAR_NOT_MASKED' | 'OTHER';

/** What the member is told when a check isn't approved. */
export const REJECT_REASONS: Record<RejectReason, string> = {
  UNREADABLE: 'The photo was blurry, too dark or cut off.',
  NAME_MISMATCH: "The name on the ID doesn't match the name on your profile.",
  NOT_ACCEPTED: "That isn't one of the IDs we accept.",
  EXPIRED: 'The ID has expired.',
  AADHAAR_NOT_MASKED: 'The full Aadhaar number was visible, so we deleted the photo. Please use a masked Aadhaar (only the last 4 digits showing).',
  OTHER: "We couldn't verify it.",
};

export interface IdCheckView {
  id: string;
  documentType: DocumentType;
  status: 'PENDING' | 'APPROVED' | 'REJECTED';
  submittedAt: string;
  decidedAt: string | null;
  rejectReason: RejectReason | null;
  rejectNote: string | null;
}

export interface VerificationStatus {
  phoneVerified: boolean;
  /** Masked, e.g. "pr•••@gmail.com"; null when the profile has no email. */
  email: string | null;
  emailVerified: boolean;
  idVerified: boolean;
  /** An approved driving licence (self-drive rentals need one). */
  licenceVerified: boolean;
  latestIdCheck: IdCheckView | null;
}

export interface EmailCodeSent {
  sentTo: string;
  expiresInSeconds: number;
  resendInSeconds: number;
  /** Only in development, where no email is really sent. */
  devCode?: string | null;
}

@Injectable({ providedIn: 'root' })
export class VerificationApi {
  private readonly http = inject(HttpClient);

  status(): Promise<VerificationStatus> {
    return firstValueFrom(this.http.get<VerificationStatus>('/api/verification'));
  }

  sendEmailCode(): Promise<EmailCodeSent> {
    return firstValueFrom(this.http.post<EmailCodeSent>('/api/verification/email/send', null));
  }

  confirmEmail(code: string): Promise<VerificationStatus> {
    return firstValueFrom(this.http.post<VerificationStatus>('/api/verification/email/confirm', { code }));
  }

  submitId(documentType: DocumentType, photo: Blob): Promise<IdCheckView> {
    const form = new FormData();
    form.append('documentType', documentType);
    form.append('photo', photo, 'id.jpg');
    return firstValueFrom(this.http.post<IdCheckView>('/api/verification/id', form));
  }
}

/**
 * Shrinks a phone photo before upload (long side at most {@code maxSide}, JPEG), so it stays well under the upload
 * limit and is quick on mobile data. The server re-processes it anyway. Falls back to the original file if the
 * browser can't read it, and lets the server explain.
 */
export async function shrinkPhoto(file: File, maxSide = 2000): Promise<Blob> {
  try {
    const bitmap = await createImageBitmap(file);
    const scale = Math.min(1, maxSide / Math.max(bitmap.width, bitmap.height));
    if (scale === 1 && file.size < 4_000_000 && /^image\/(jpeg|png)$/.test(file.type)) {
      bitmap.close();
      return file;
    }
    const canvas = document.createElement('canvas');
    canvas.width = Math.round(bitmap.width * scale);
    canvas.height = Math.round(bitmap.height * scale);
    const ctx = canvas.getContext('2d');
    if (!ctx) return file;
    ctx.fillStyle = '#fff';
    ctx.fillRect(0, 0, canvas.width, canvas.height);
    ctx.drawImage(bitmap, 0, 0, canvas.width, canvas.height);
    bitmap.close();
    const blob = await new Promise<Blob | null>((resolve) => canvas.toBlob(resolve, 'image/jpeg', 0.9));
    return blob ?? file;
  } catch {
    return file;
  }
}
