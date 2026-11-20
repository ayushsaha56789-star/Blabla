import { effect, inject, Injectable, signal, untracked } from '@angular/core';
import { AuthService } from './auth.service';
import { ChatApi } from './chat-api';
import { FeaturesService } from './features.service';
import { RidesApi } from './rides-api';
import { SupportApi } from './support-api';

/**
 * Counts for the badges in the top bar: booking requests waiting for this member's answer, upcoming trips, chats
 * with unread messages, and help requests support has replied to. Refreshed on login, every 30 seconds while the
 * tab is visible, and after anything that changes them.
 */
@Injectable({ providedIn: 'root' })
export class TripsService {
  private readonly rides = inject(RidesApi);
  private readonly chats = inject(ChatApi);
  private readonly support = inject(SupportApi);
  private readonly auth = inject(AuthService);
  private readonly features = inject(FeaturesService);

  readonly pendingRequests = signal(0);
  readonly upcomingTrips = signal(0);
  readonly unreadChats = signal(0);
  readonly supportReplies = signal(0);

  constructor() {
    effect(() => {
      const loggedIn = !!this.auth.user()?.id;
      // Read the switches here so the counts refresh when the owner turns an area on or off.
      this.features.isOn('rides');
      this.features.isOn('chat');
      untracked(() => (loggedIn ? void this.refresh() : this.clear()));
    });
    setInterval(() => {
      if (document.visibilityState === 'visible' && this.auth.isLoggedIn()) void this.refresh();
    }, 30_000);
  }

  async refresh(): Promise<void> {
    if (!this.auth.isLoggedIn()) return;
    await Promise.all([this.refreshTrips(), this.refreshChats(), this.refreshSupport()]);
  }

  async refreshChats(): Promise<void> {
    if (!this.auth.isLoggedIn() || !this.features.isOn('chat')) {
      this.unreadChats.set(0);
      return;
    }
    try {
      this.unreadChats.set((await this.chats.unread()).conversations);
    } catch {
      // A badge is a convenience; try again next time.
    }
  }

  async refreshSupport(): Promise<void> {
    if (!this.auth.isLoggedIn()) return;
    try {
      this.supportReplies.set((await this.support.newReplies()).count);
    } catch {
      // Same as above.
    }
  }

  private async refreshTrips(): Promise<void> {
    if (!this.features.isOn('rides')) {
      this.pendingRequests.set(0);
      this.upcomingTrips.set(0);
      return;
    }
    try {
      const s = await this.rides.tripsSummary();
      this.pendingRequests.set(s.pendingRequests);
      this.upcomingTrips.set(s.upcomingTrips);
    } catch {
      // Same as above.
    }
  }

  private clear(): void {
    this.pendingRequests.set(0);
    this.upcomingTrips.set(0);
    this.unreadChats.set(0);
    this.supportReplies.set(0);
  }
}
