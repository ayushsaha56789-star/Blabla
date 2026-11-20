import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { computed, inject, Injectable, signal } from '@angular/core';
import { Router } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import { AuthResponse, Me, OtpRequestResponse, StaffInfo } from './api.models';
import { problemOf } from './errors';
import { storageGet, storageSet } from './util';

/** Remembers that this browser had a session, so we only try to restore one when it makes sense. */
const SESSION_HINT = 'sc.session';

export interface Suspension {
  reason: string | null;
  until: string | null;
}

/**
 * Login state. The short-lived access token lives only in memory; the long-lived refresh token is an
 * HttpOnly cookie the browser sends to /api/auth automatically, so scripts can never read it.
 */
@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly router = inject(Router);

  private readonly token = signal<string | null>(null);
  private readonly me = signal<Me | null>(null);
  private readonly staffInfo = signal<StaffInfo | null>(null);
  private readonly suspended = signal<Suspension | null>(null);
  private refreshing: Promise<boolean> | null = null;

  readonly user = this.me.asReadonly();
  readonly staff = this.staffInfo.asReadonly();
  readonly suspension = this.suspended.asReadonly();
  readonly isLoggedIn = computed(() => this.me() !== null);

  accessToken(): string | null {
    return this.token();
  }

  /** Called once at start-up. */
  async restoreSession(): Promise<void> {
    if (storageGet(SESSION_HINT)) {
      await this.refresh();
    }
  }

  requestOtp(phone: string): Promise<OtpRequestResponse> {
    return firstValueFrom(this.http.post<OtpRequestResponse>('/api/auth/otp/request', { phone }));
  }

  /** {@code restored}: this login cancelled the member's request to delete their account. */
  async verifyOtp(phone: string, code: string): Promise<{ me: Me; restored: boolean }> {
    const res = await firstValueFrom(this.http.post<AuthResponse>('/api/auth/otp/verify', { phone, code }));
    this.startSession(res);
    return { me: res.user, restored: !!res.accountRestored };
  }

  /** Gets a fresh access token. Concurrent callers share one request. */
  refresh(): Promise<boolean> {
    this.refreshing ??= firstValueFrom(this.http.post<AuthResponse>('/api/auth/refresh', {}))
      .then((res) => {
        this.startSession(res);
        return true;
      })
      .catch((err: unknown) => {
        // Only a definite "no" from the server ends the session; a network blip should not.
        const definite = err instanceof HttpErrorResponse && (err.status === 401 || err.status === 403);
        if (err instanceof HttpErrorResponse && problemOf(err).code === 'account_suspended') {
          this.markSuspended(problemOf(err).reason ?? null, problemOf(err).until ?? null);
        }
        this.endSession(definite);
        return false;
      })
      .finally(() => (this.refreshing = null));
    return this.refreshing;
  }

  async logout(): Promise<void> {
    try {
      await firstValueFrom(this.http.post('/api/auth/logout', {}));
    } catch {
      // Log out locally even if the server can't be reached.
    }
    this.endSession(true);
    this.suspended.set(null);
    await this.router.navigateByUrl('/');
  }

  /** Keeps the cached user in sync after profile changes. */
  setUser(me: Me): void {
    this.me.set(me);
  }

  /** Called when the API says this account is suspended; the app then shows the suspension screen. */
  markSuspended(reason: string | null, until: string | null): void {
    this.suspended.set({ reason, until });
  }

  private startSession(res: AuthResponse): void {
    this.token.set(res.accessToken);
    this.me.set(res.user);
    this.staffInfo.set(res.staff ?? null);
    this.suspended.set(null);
    storageSet(SESSION_HINT, '1');
  }

  private endSession(forgetHint: boolean): void {
    this.token.set(null);
    this.me.set(null);
    this.staffInfo.set(null);
    if (forgetHint) storageSet(SESSION_HINT, null);
  }
}
