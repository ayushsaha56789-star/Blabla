import { effect, inject, Injectable, signal, untracked } from '@angular/core';
import { AuthService } from './auth.service';
import { FeaturesService } from './features.service';
import { RidesApi } from './rides-api';

/**
 * Counts for the "My rides" badge: booking requests waiting for this member's answer. Refreshed on login, every two
 * minutes while the tab is open, and after anything that changes a booking.
 */
@Injectable({ providedIn: 'root' })
export class TripsService {
  private readonly api = inject(RidesApi);
  private readonly auth = inject(AuthService);
  private readonly features = inject(FeaturesService);

  readonly pendingRequests = signal(0);
  readonly upcomingTrips = signal(0);

  constructor() {
    effect(() => {
      const loggedIn = !!this.auth.user()?.id;
      const ridesOn = this.features.isOn('rides');
      untracked(() => (loggedIn && ridesOn ? void this.refresh() : this.clear()));
    });
    setInterval(() => {
      if (document.visibilityState === 'visible' && this.auth.isLoggedIn()) void this.refresh();
    }, 120_000);
  }

  async refresh(): Promise<void> {
    if (!this.auth.isLoggedIn() || !this.features.isOn('rides')) return;
    try {
      const s = await this.api.tripsSummary();
      this.pendingRequests.set(s.pendingRequests);
      this.upcomingTrips.set(s.upcomingTrips);
    } catch {
      // The badge is a convenience; try again next time.
    }
  }

  private clear(): void {
    this.pendingRequests.set(0);
    this.upcomingTrips.set(0);
  }
}
