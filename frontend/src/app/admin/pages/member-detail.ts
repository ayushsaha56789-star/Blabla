import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, effect, inject, input, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { problemOf } from '../../core/errors';
import { ToastService } from '../../core/toast.service';
import { todayIso } from '../../core/util';
import { Avatar } from '../../shared/avatar';
import { Icon } from '../../shared/icon';
import { PhonePipe } from '../../shared/phone.pipe';
import { AdminApi } from '../admin-api';
import { MemberDetailResponse } from '../admin.models';
import { auditStyle, fullTime, timeAgo } from '../admin-ui';
import { ConfirmationCancelled } from '../confirm-code.service';
import { ConsoleSessionService } from '../console-session.service';

@Component({
  selector: 'app-admin-member-detail',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, DatePipe, Avatar, Icon, PhonePipe],
  templateUrl: './member-detail.html',
})
export class MemberDetailPage {
  private readonly api = inject(AdminApi);
  private readonly toast = inject(ToastService);
  protected readonly session = inject(ConsoleSessionService);

  /** From the route: /admin/members/:id */
  readonly id = input.required<string>();

  protected readonly data = signal<MemberDetailResponse | null>(null);
  protected readonly error = signal<string | null>(null);
  protected readonly phone = signal<string | null>(null);
  protected readonly busy = signal(false);

  protected readonly area = signal('ALL');
  protected readonly reason = signal('');
  protected readonly until = signal('');
  protected readonly minDate = todayIso();

  protected readonly active = computed(() => this.data()?.member.restrictions.filter((r) => r.active) ?? []);
  protected readonly past = computed(() => this.data()?.member.restrictions.filter((r) => !r.active) ?? []);
  protected readonly isYou = computed(() => this.data()?.member.id === this.session.me()?.userId);
  /** Mirrors the server rules: nobody restricts a super admin or themselves; only super admins restrict staff. */
  protected readonly canRestrict = computed(() => {
    const d = this.data();
    if (!d || this.isYou() || !this.session.can('users.restrict')) return false;
    if (d.staffRole === 'SUPER_ADMIN') return false;
    return !d.staffRole || this.session.me()?.role === 'SUPER_ADMIN';
  });

  protected readonly timeAgo = timeAgo;
  protected readonly fullTime = fullTime;
  protected readonly auditStyle = auditStyle;

  constructor() {
    effect(() => void this.load(this.id()));
  }

  protected async reveal(): Promise<void> {
    await this.act(async () => this.phone.set((await this.api.revealPhone(this.id())).phone));
  }

  protected async restrict(event: Event): Promise<void> {
    event.preventDefault();
    if (this.reason().trim().length < 3) {
      this.toast.error('Please give a short reason. The member will see it.');
      return;
    }
    await this.act(async () => {
      await this.api.restrict(this.id(), { area: this.area(), reason: this.reason().trim(), until: this.until() || null });
      this.toast.success(this.area() === 'ALL' ? 'Account suspended.' : 'Restriction added.');
      this.reason.set('');
      this.until.set('');
      await this.load(this.id());
    });
  }

  protected async lift(restrictionId: string): Promise<void> {
    await this.act(async () => {
      await this.api.lift(this.id(), restrictionId);
      this.toast.success('Restriction lifted.');
      await this.load(this.id());
    });
  }

  protected async toggleBeta(): Promise<void> {
    const d = this.data();
    if (!d) return;
    await this.act(async () => {
      this.data.set(await this.api.setBetaTester(this.id(), !d.member.betaTester));
      this.toast.success(d.member.betaTester ? 'Removed from beta testers.' : 'Now a beta tester.');
    });
  }

  private async load(id: string): Promise<void> {
    this.error.set(null);
    this.phone.set(null);
    try {
      this.data.set(await this.api.member(id));
    } catch (e) {
      this.error.set(problemOf(e).detail);
    }
  }

  private async act(work: () => Promise<void>): Promise<void> {
    this.busy.set(true);
    try {
      await work();
    } catch (e) {
      if (!(e instanceof ConfirmationCancelled)) this.toast.error(problemOf(e).detail);
    } finally {
      this.busy.set(false);
    }
  }
}
