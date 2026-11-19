import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { computed, inject, Injectable, signal } from '@angular/core';
import { Router } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import { AuthResponse, Me, OtpRequestResponse } from './api.models';
import { storageGet, storageSet } from './util';

/** Remembers that this browser had a session, so we only try to restore one when it makes sense. */
const SESSION_HINT = 'sc.session';

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
  private refreshing: Promise<boolean> | null = null;

  readonly user = this.me.asReadonly();
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

  async verifyOtp(phone: string, code: string): Promise<Me> {
    const res = await firstValueFrom(this.http.post<AuthResponse>('/api/auth/otp/verify', { phone, code }));
    this.startSession(res);
    return res.user;
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
    await this.router.navigateByUrl('/');
  }

  /** Keeps the cached user in sync after profile changes. */
  setUser(me: Me): void {
    this.me.set(me);
  }

  private startSession(res: AuthResponse): void {
    this.token.set(res.accessToken);
    this.me.set(res.user);
    storageSet(SESSION_HINT, '1');
  }

  private endSession(forgetHint: boolean): void {
    this.token.set(null);
    this.me.set(null);
    if (forgetHint) storageSet(SESSION_HINT, null);
  }
}
