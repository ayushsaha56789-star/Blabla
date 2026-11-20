import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, inject, OnInit, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { problemOf } from '../../core/errors';
import { SupportCategory } from '../../core/support-api';
import { Avatar } from '../../shared/avatar';
import { Icon } from '../../shared/icon';
import { AdminApi } from '../admin-api';
import { StaffTicketRow } from '../admin.models';
import { timeAgo } from '../admin-ui';

type Filter = 'OPEN' | 'ANSWERED' | 'CLOSED' | 'ALL';

/** Short labels for staff; members see friendlier ones. */
export const TOPIC: Record<SupportCategory, string> = {
  BOOKING: 'Booking',
  OFFERING: 'Offering a ride',
  PAYMENT: 'Money',
  SAFETY: 'Safety',
  ACCOUNT: 'Account',
  PRIVACY: 'Privacy',
  BUG: 'Bug',
  GRIEVANCE: 'Grievance',
  OTHER: 'Other',
};

export const TICKET_STATUS: Record<StaffTicketRow['status'], { text: string; tone: string }> = {
  OPEN: { text: 'Waiting for us', tone: 'warning' },
  ANSWERED: { text: 'Answered', tone: 'success' },
  CLOSED: { text: 'Closed', tone: 'muted' },
};

/** Members' help requests: reply to each one within 24 hours. */
@Component({
  selector: 'app-admin-support',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [DatePipe, RouterLink, Avatar, Icon],
  template: `
    <header class="flex flex-wrap items-end justify-between gap-4">
      <div>
        <p class="kicker">Members</p>
        <h1 class="mt-1 text-3xl font-extrabold tracking-tight">Help requests</h1>
        <p class="mt-1 text-slate-600 dark:text-slate-400">Reply to every request within 24 hours. The oldest waiting request is first.</p>
      </div>
      <div class="segmented grid-cols-4" role="tablist" aria-label="Which requests">
        @for (f of filters; track f.key) {
          <button type="button" role="tab" class="segment px-3 sm:px-4" [class.segment-on]="filter() === f.key" [attr.aria-selected]="filter() === f.key" (click)="show(f.key)">{{ f.label }}</button>
        }
      </div>
    </header>

    @if (error()) { <p class="field-error mt-4" role="alert">{{ error() }}</p> }

    @if (tickets(); as list) {
      @if (!list.length) {
        <div class="card mt-6 p-10 text-center">
          <app-icon name="checkCircle" [size]="32" class="text-brand-600" />
          <p class="mt-3 font-semibold">{{ filter() === 'OPEN' ? 'Nobody is waiting for a reply.' : 'Nothing here yet.' }}</p>
        </div>
      } @else {
        <ul class="card mt-6 divide-y divide-slate-100 dark:divide-slate-800">
          @for (t of list; track t.id) {
            <li>
              <a [routerLink]="['/admin/support', t.id]" class="flex items-center gap-3 p-4 hover:bg-slate-50 dark:hover:bg-slate-800/50" [class]="t.overdue ? 'border-l-4 border-rose-500' : ''">
                <app-avatar [url]="t.member.avatarUrl" [name]="t.member.displayName" [size]="40" />
                <span class="min-w-0 flex-1">
                  <span class="block truncate font-semibold">{{ t.subject }}</span>
                  <span class="block truncate text-sm text-slate-500 dark:text-slate-400">{{ t.member.displayName }} · {{ topic[t.category] }}</span>
                </span>
                <span class="flex shrink-0 flex-col items-end gap-1 text-xs">
                  @if (t.overdue) { <span class="badge badge-danger">Overdue</span> }
                  @else if (t.status === 'OPEN' && !t.firstResponseAt) { <span class="badge badge-warning">Reply by {{ t.respondBy | date: 'd MMM, h:mm a' }}</span> }
                  @else { <span class="badge" [class]="'tone-' + status[t.status].tone">{{ status[t.status].text }}</span> }
                  <time class="text-slate-500" [title]="t.updatedAt | date: 'd MMM y, h:mm a'">{{ ago(t.updatedAt) }}</time>
                </span>
              </a>
            </li>
          }
        </ul>
      }
    } @else if (!error()) {
      <div class="skeleton mt-6 h-40"></div>
    }
  `,
})
export class SupportPage implements OnInit {
  private readonly api = inject(AdminApi);

  protected readonly filters: { key: Filter; label: string }[] = [
    { key: 'OPEN', label: 'Waiting' },
    { key: 'ANSWERED', label: 'Answered' },
    { key: 'CLOSED', label: 'Closed' },
    { key: 'ALL', label: 'All' },
  ];
  protected readonly filter = signal<Filter>('OPEN');
  protected readonly tickets = signal<StaffTicketRow[] | null>(null);
  protected readonly error = signal<string | null>(null);
  protected readonly topic = TOPIC;
  protected readonly status = TICKET_STATUS;
  protected readonly ago = timeAgo;

  ngOnInit(): void {
    void this.load();
  }

  protected show(filter: Filter): void {
    this.filter.set(filter);
    this.tickets.set(null);
    void this.load();
  }

  private async load(): Promise<void> {
    try {
      this.tickets.set(await this.api.tickets(this.filter()));
      this.error.set(null);
    } catch (e) {
      this.error.set(problemOf(e).detail);
    }
  }
}
