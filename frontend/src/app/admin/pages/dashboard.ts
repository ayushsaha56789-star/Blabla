import { DecimalPipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, inject, OnInit, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { problemOf } from '../../core/errors';
import { Icon } from '../../shared/icon';
import { AdminApi } from '../admin-api';
import { Dashboard } from '../admin.models';
import { auditStyle, fullTime, timeAgo } from '../admin-ui';
import { ConsoleSessionService } from '../console-session.service';
import { SignupsChart } from './signups-chart';

@Component({
  selector: 'app-admin-dashboard',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, DecimalPipe, Icon, SignupsChart],
  templateUrl: './dashboard.html',
})
export class DashboardPage implements OnInit {
  private readonly api = inject(AdminApi);
  protected readonly session = inject(ConsoleSessionService);

  protected readonly data = signal<Dashboard | null>(null);
  protected readonly error = signal<string | null>(null);
  protected readonly timeAgo = timeAgo;
  protected readonly fullTime = fullTime;
  protected readonly auditStyle = auditStyle;

  protected readonly greeting = computed(() => {
    const h = new Date().getHours();
    return h < 12 ? 'Good morning' : h < 17 ? 'Good afternoon' : 'Good evening';
  });
  protected readonly firstName = computed(() => this.session.me()?.displayName.split(' ')[0] ?? '');
  protected readonly today = new Date().toLocaleDateString('en-IN', { weekday: 'long', day: 'numeric', month: 'long' });

  async ngOnInit(): Promise<void> {
    try {
      this.data.set(await this.api.dashboard());
    } catch (e) {
      this.error.set(problemOf(e).detail);
    }
  }
}
