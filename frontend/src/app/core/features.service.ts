import { HttpClient } from '@angular/common/http';
import { computed, DestroyRef, effect, inject, Injectable, signal, untracked } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import { FeaturesResponse } from './api.models';
import { AuthService } from './auth.service';

/**
 * Which features the owner has switched on for this member (from GET /api/features). Reloaded when the member
 * logs in or out, every minute, and when the tab comes back into view, so a flipped switch shows up quickly.
 * The server enforces every switch too; this only decides what the website shows.
 */
@Injectable({ providedIn: 'root' })
export class FeaturesService {
  private readonly http = inject(HttpClient);
  private readonly auth = inject(AuthService);

  private readonly enabled = signal<ReadonlySet<string>>(new Set());
  private readonly maintenanceOn = signal(false);
  private started = false;

  readonly maintenance = this.maintenanceOn.asReadonly();
  readonly enabledKeys = computed(() => this.enabled());

  constructor() {
    // Reload whenever the logged-in member changes (log in, log out, switch account).
    effect(() => {
      this.auth.user()?.id;
      untracked(() => {
        if (this.started) void this.load();
      });
    });
  }

  isOn(key: string): boolean {
    return this.enabled().has(key);
  }

  async load(): Promise<void> {
    try {
      const res = await firstValueFrom(this.http.get<FeaturesResponse>('/api/features'));
      this.enabled.set(new Set(res.enabled));
      this.maintenanceOn.set(res.maintenance);
    } catch {
      // Keep the last known state; the next poll will try again.
    }
  }

  /** Starts the periodic refresh (called once at start-up). */
  start(destroyRef?: DestroyRef): void {
    if (this.started) return;
    this.started = true;
    const timer = setInterval(() => void this.load(), 60_000);
    const onVisible = () => {
      if (document.visibilityState === 'visible') void this.load();
    };
    document.addEventListener('visibilitychange', onVisible);
    destroyRef?.onDestroy(() => {
      clearInterval(timer);
      document.removeEventListener('visibilitychange', onVisible);
    });
  }

  setMaintenance(on: boolean): void {
    this.maintenanceOn.set(on);
  }
}

/** Route guard: the page exists only while its feature switch is on for this member. */
export function featureGuard(key: string): CanActivateFn {
  return () => inject(FeaturesService).isOn(key) || inject(Router).createUrlTree(['/']);
}
