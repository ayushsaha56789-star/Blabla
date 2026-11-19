import { computed, inject, Injectable, signal } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AdminApi } from './admin-api';
import { AdminMe, StaffStatus, TotpSetup } from './admin.models';

/**
 * The staff console session. Its token is kept in memory only (never in browser storage), so reloading the page
 * asks for a fresh 2FA code — a deliberate trade of a little convenience for safety.
 */
@Injectable({ providedIn: 'root' })
export class ConsoleSessionService {
  private readonly api = inject(AdminApi);

  readonly status = signal<StaffStatus | null>(null);
  readonly me = signal<AdminMe | null>(null);
  /** Set when the server ended the session (idle 15 minutes, signed out elsewhere, or no longer staff). */
  readonly timedOut = signal(false);
  /** Recovery codes from a just-finished setup; the gate shows them until the staff member confirms they saved them. */
  readonly freshCodes = signal<string[] | null>(null);
  private readonly token = signal<string | null>(null);

  readonly active = computed(() => this.token() !== null && this.me() !== null);

  consoleToken(): string | null {
    return this.token();
  }

  can(permission: string): boolean {
    return this.me()?.permissions.includes(permission) ?? false;
  }

  async loadStatus(): Promise<StaffStatus> {
    const status = await this.api.staffStatus();
    this.status.set(status);
    return status;
  }

  startSetup(): Promise<TotpSetup> {
    return this.api.startSetup();
  }

  /** Finishes 2FA setup; returns the one-time recovery codes to show the staff member. */
  async confirmSetup(code: string): Promise<string[]> {
    const done = await this.api.confirmSetup(code);
    this.freshCodes.set(done.recoveryCodes);
    await this.begin(done.session.token);
    this.status.update((s) => (s ? { ...s, totpEnabled: true, recoveryCodesLeft: done.recoveryCodes.length } : s));
    return done.recoveryCodes;
  }

  async signIn(code: string | null, recoveryCode: string | null): Promise<void> {
    const session = await this.api.signIn(code, recoveryCode);
    await this.begin(session.token);
  }

  async signOut(): Promise<void> {
    try {
      await this.api.signOut();
    } catch {
      // Ending the session locally is what matters to the person in front of the screen.
    }
    this.end(false);
  }

  end(timedOut: boolean): void {
    this.token.set(null);
    this.me.set(null);
    this.timedOut.set(timedOut);
  }

  private async begin(token: string): Promise<void> {
    this.token.set(token);
    this.timedOut.set(false);
    try {
      this.me.set(await this.api.me());
    } catch (e) {
      this.token.set(null);
      throw e;
    }
  }
}

/** Console pages that need a permission. Before sign-in the shell shows its gate, so the guard lets it through. */
export function permissionGuard(permission: string): CanActivateFn {
  return () => {
    const session = inject(ConsoleSessionService);
    if (!session.active() || session.can(permission)) return true;
    return inject(Router).createUrlTree(['/admin']);
  };
}
