import { NgTemplateOutlet } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, inject, OnInit, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { NavigationEnd, Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { filter } from 'rxjs';
import { problemOf } from '../../core/errors';
import { Avatar } from '../../shared/avatar';
import { Icon, IconName } from '../../shared/icon';
import { Logo } from '../../shared/logo';
import { ThemeSwitcher } from '../../shared/theme-switcher';
import { ConsoleSessionService } from '../console-session.service';
import { StaffGate } from '../gate/staff-gate';
import { ConfirmCodeDialog } from '../ui/confirm-code-dialog';

interface NavItem {
  path: string;
  label: string;
  icon: IconName;
  permission: string | null;
  exact?: boolean;
}

const NAV: NavItem[] = [
  { path: '/admin', label: 'Dashboard', icon: 'grid', permission: 'users.view', exact: true },
  { path: '/admin/members', label: 'Members', icon: 'users', permission: 'users.view' },
  { path: '/admin/rides', label: 'Rides', icon: 'car', permission: 'users.view' },
  { path: '/admin/safety', label: 'SOS alerts', icon: 'shieldAlert', permission: 'support.tickets' },
  { path: '/admin/reports', label: 'Reports', icon: 'flag', permission: 'content.moderate' },
  { path: '/admin/events', label: 'Events', icon: 'calendar', permission: 'content.moderate' },
  { path: '/admin/support', label: 'Help requests', icon: 'chat', permission: 'support.tickets' },
  { path: '/admin/verifications', label: 'ID checks', icon: 'idCard', permission: 'verification.review' },
  { path: '/admin/rentals', label: 'Rentals', icon: 'car', permission: 'rentals.manage' },
  { path: '/admin/drivers', label: 'Drivers', icon: 'steering', permission: 'drivers.manage' },
  { path: '/admin/payments', label: 'Payments', icon: 'rupee', permission: 'payments.refund' },
  { path: '/admin/features', label: 'Feature switches', icon: 'toggles', permission: 'features.manage' },
  { path: '/admin/staff', label: 'Staff', icon: 'shield', permission: 'staff.manage' },
  { path: '/admin/audit', label: 'Audit log', icon: 'history', permission: 'audit.view' },
  { path: '/admin/security', label: 'My security', icon: 'key', permission: null },
];

@Component({
  selector: 'app-admin-shell',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [NgTemplateOutlet, RouterOutlet, RouterLink, RouterLinkActive, Icon, Logo, Avatar, ThemeSwitcher, StaffGate, ConfirmCodeDialog],
  templateUrl: './admin-shell.html',
})
export class AdminShell implements OnInit {
  protected readonly session = inject(ConsoleSessionService);
  private readonly router = inject(Router);

  protected readonly loadError = signal<string | null>(null);
  protected readonly drawerOpen = signal(false);
  protected readonly nav = computed(() => NAV.filter((n) => n.permission === null || this.session.can(n.permission)));

  protected readonly state = computed(() => {
    if (this.loadError()) return 'error';
    const status = this.session.status();
    if (!status) return 'loading';
    if (!status.staff) return 'not-staff';
    if (!this.session.active() || this.session.freshCodes()) return 'gate';
    return 'console';
  });

  constructor() {
    this.router.events.pipe(filter((e) => e instanceof NavigationEnd), takeUntilDestroyed())
      .subscribe(() => this.drawerOpen.set(false));
  }

  async ngOnInit(): Promise<void> {
    try {
      await this.session.loadStatus();
    } catch (e) {
      this.loadError.set(problemOf(e).detail);
    }
  }

  protected async signOut(): Promise<void> {
    await this.session.signOut();
    await this.router.navigateByUrl('/');
  }
}
