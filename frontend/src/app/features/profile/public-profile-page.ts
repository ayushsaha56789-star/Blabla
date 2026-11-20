import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, effect, inject, input, signal, untracked } from '@angular/core';
import { RouterLink } from '@angular/router';
import { PublicProfile } from '../../core/api.models';
import { AuthService } from '../../core/auth.service';
import { problemOf } from '../../core/errors';
import { FeaturesService } from '../../core/features.service';
import { ModerationApi, ReportTarget } from '../../core/moderation-api';
import { Review, ReviewsApi } from '../../core/reviews-api';
import { ToastService } from '../../core/toast.service';
import { UserApi } from '../../core/user-api';
import { Avatar } from '../../shared/avatar';
import { Icon } from '../../shared/icon';
import { ReportDialog } from '../../shared/report-dialog';
import { Stars } from '../../shared/stars';
import { chatOption, preferenceBadges } from './prefs';

@Component({
  selector: 'app-public-profile-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [DatePipe, RouterLink, Avatar, Icon, Stars, ReportDialog],
  templateUrl: './public-profile-page.html',
})
export class PublicProfilePage {
  private readonly api = inject(UserApi);
  private readonly reviewsApi = inject(ReviewsApi);
  private readonly moderation = inject(ModerationApi);
  private readonly toast = inject(ToastService);
  protected readonly auth = inject(AuthService);
  protected readonly features = inject(FeaturesService);

  /** From the route: /u/:id */
  readonly id = input.required<string>();

  protected readonly profile = signal<PublicProfile | null>(null);
  protected readonly error = signal<string | null>(null);
  protected readonly reviews = signal<Review[]>([]);
  protected readonly reviewTotal = signal(0);
  protected readonly loadingMore = signal(false);
  protected readonly blocked = signal(false);
  protected readonly confirmBlock = signal(false);
  protected readonly busy = signal(false);
  protected readonly reporting = signal<{ type: ReportTarget; id: string } | null>(null);
  protected readonly isMe = computed(() => this.auth.user()?.id === this.id());
  protected readonly chatOption = chatOption;
  protected readonly preferenceBadges = preferenceBadges;
  private reviewPage = 0;

  constructor() {
    effect(() => {
      const id = this.id();
      untracked(() => void this.load(id));
    });
  }

  protected async moreReviews(): Promise<void> {
    this.loadingMore.set(true);
    try {
      const page = await this.reviewsApi.about(this.id(), this.reviewPage + 1);
      this.reviewPage = page.page;
      this.reviews.update((list) => [...list, ...page.items]);
    } finally {
      this.loadingMore.set(false);
    }
  }

  protected async toggleBlock(): Promise<void> {
    const p = this.profile();
    if (!p) return;
    this.busy.set(true);
    try {
      if (this.blocked()) {
        await this.moderation.unblock(p.id);
        this.blocked.set(false);
        this.toast.success(`${p.displayName} is unblocked.`);
      } else {
        await this.moderation.block(p.id);
        this.blocked.set(true);
        this.toast.success(`${p.displayName} is blocked. They can't message you or book your rides.`);
      }
      this.confirmBlock.set(false);
    } catch (e) {
      this.toast.error(problemOf(e).detail);
    } finally {
      this.busy.set(false);
    }
  }

  private async load(id: string): Promise<void> {
    this.profile.set(null);
    this.error.set(null);
    this.reviews.set([]);
    this.reviewPage = 0;
    try {
      this.profile.set(await this.api.publicProfile(id));
    } catch (e) {
      const p = problemOf(e);
      this.error.set(p.status === 404 || p.status === 400 ? "We couldn't find that member." : p.detail);
      return;
    }
    if (this.auth.isLoggedIn() && !this.isMe()) {
      this.moderation.myBlocks().then((list) => this.blocked.set(list.some((m) => m.id === id))).catch(() => undefined);
    }
    if (this.features.isOn('reviews')) {
      try {
        const page = await this.reviewsApi.about(id);
        this.reviews.set(page.items);
        this.reviewTotal.set(page.total);
      } catch {
        // Reviews are extra; the profile still shows.
      }
    }
  }
}
