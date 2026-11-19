import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, inject, OnInit, signal } from '@angular/core';
import { problemOf } from '../../core/errors';
import { ToastService } from '../../core/toast.service';
import { formatIndianMobile, isValidIndianMobile } from '../../core/util';
import { Avatar } from '../../shared/avatar';
import { Icon } from '../../shared/icon';
import { AdminApi } from '../admin-api';
import { RoleInfo, StaffRow } from '../admin.models';
import { timeAgo } from '../admin-ui';
import { ConfirmationCancelled } from '../confirm-code.service';

@Component({
  selector: 'app-admin-staff',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [DatePipe, Avatar, Icon],
  templateUrl: './staff.html',
})
export class StaffPage implements OnInit {
  private readonly api = inject(AdminApi);
  private readonly toast = inject(ToastService);

  protected readonly staff = signal<StaffRow[] | null>(null);
  protected readonly roles = signal<RoleInfo[]>([]);
  protected readonly error = signal<string | null>(null);
  protected readonly busy = signal(false);
  protected readonly adding = signal(false);
  protected readonly phone = signal('');
  protected readonly newRole = signal('MODERATOR');
  protected readonly confirmRemove = signal<string | null>(null);
  protected readonly timeAgo = timeAgo;
  protected readonly isValidPhone = isValidIndianMobile;

  async ngOnInit(): Promise<void> {
    try {
      const [staff, roles] = await Promise.all([this.api.staff(), this.api.roles()]);
      this.staff.set(staff);
      this.roles.set(roles);
    } catch (e) {
      this.error.set(problemOf(e).detail);
    }
  }

  protected onPhone(value: string, el: HTMLInputElement): void {
    const formatted = formatIndianMobile(value);
    el.value = formatted;
    this.phone.set(formatted);
  }

  protected async add(event: Event): Promise<void> {
    event.preventDefault();
    await this.act(async () => {
      const added = await this.api.addStaff(this.phone(), this.newRole());
      this.staff.update((list) => [...(list ?? []), added]);
      this.toast.success(`${added.displayName} is now ${added.roleName}. They'll set up two-step verification on first sign-in.`);
      this.phone.set('');
      this.adding.set(false);
    });
  }

  protected async changeRole(row: StaffRow, role: string): Promise<void> {
    if (role === row.role) return;
    await this.act(
      async () => {
        const updated = await this.api.changeRole(row.userId, role);
        this.staff.update((list) => list?.map((s) => (s.userId === updated.userId ? updated : s)) ?? list);
        this.toast.success(`${updated.displayName} is now ${updated.roleName}.`);
      },
      () => this.staff.update((list) => (list ? [...list] : list)),
    );
  }

  protected async remove(row: StaffRow): Promise<void> {
    await this.act(async () => {
      await this.api.removeStaff(row.userId);
      this.staff.update((list) => list?.filter((s) => s.userId !== row.userId) ?? list);
      this.confirmRemove.set(null);
      this.toast.success(`${row.displayName} was removed from the staff.`);
    });
  }

  protected async resetTwoFactor(row: StaffRow): Promise<void> {
    await this.act(async () => {
      await this.api.resetTwoFactor(row.userId);
      this.staff.update((list) => list?.map((s) => (s.userId === row.userId ? { ...s, totpEnabled: false } : s)) ?? list);
      this.toast.success(`${row.displayName} will set up two-step verification again at their next sign-in.`);
    });
  }

  private async act(work: () => Promise<void>, onError?: () => void): Promise<void> {
    this.busy.set(true);
    try {
      await work();
    } catch (e) {
      onError?.();
      if (!(e instanceof ConfirmationCancelled)) this.toast.error(problemOf(e).detail);
    } finally {
      this.busy.set(false);
    }
  }
}
