import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, effect, inject, input, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { PublicProfile } from '../../core/api.models';
import { AuthService } from '../../core/auth.service';
import { problemOf } from '../../core/errors';
import { UserApi } from '../../core/user-api';
import { Avatar } from '../../shared/avatar';
import { Icon } from '../../shared/icon';
import { chatOption, preferenceBadges } from './prefs';

@Component({
  selector: 'app-public-profile-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [DatePipe, RouterLink, Avatar, Icon],
  templateUrl: './public-profile-page.html',
})
export class PublicProfilePage {
  private readonly api = inject(UserApi);
  protected readonly auth = inject(AuthService);

  /** From the route: /u/:id */
  readonly id = input.required<string>();

  protected readonly profile = signal<PublicProfile | null>(null);
  protected readonly error = signal<string | null>(null);
  protected readonly chatOption = chatOption;
  protected readonly preferenceBadges = preferenceBadges;

  constructor() {
    effect(() => void this.load(this.id()));
  }

  private async load(id: string): Promise<void> {
    this.profile.set(null);
    this.error.set(null);
    try {
      this.profile.set(await this.api.publicProfile(id));
    } catch (e) {
      const p = problemOf(e);
      this.error.set(p.status === 404 || p.status === 400 ? "We couldn't find that member." : p.detail);
    }
  }
}
