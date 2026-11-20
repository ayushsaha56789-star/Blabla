import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, inject, OnInit, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { problemOf } from '../../core/errors';
import { REASONS } from '../../core/moderation-api';
import { ToastService } from '../../core/toast.service';
import { Avatar } from '../../shared/avatar';
import { Icon } from '../../shared/icon';
import { AdminApi } from '../admin-api';
import { ReportRow } from '../admin.models';
import { timeAgo } from '../admin-ui';

type Action = 'DISMISS' | 'REMOVE_CONTENT' | 'ACTION_TAKEN';

const WHAT: Record<string, string> = { MEMBER: 'a member', RIDE: 'a ride', MESSAGE: 'a chat message', REVIEW: 'a review' };

/** Members' reports. IT Rules 2021: acknowledge within 24 hours, resolve within 15 days. */
@Component({
  selector: 'app-admin-reports',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [DatePipe, RouterLink, Avatar, Icon],
  template: `
    <header class="flex flex-wrap items-end justify-between gap-4">
      <div>
        <p class="kicker">Trust and safety</p>
        <h1 class="mt-1 text-3xl font-extrabold tracking-tight">Reports</h1>
        <p class="mt-1 text-slate-600 dark:text-slate-400">Acknowledge each report within 24 hours and resolve it within 15 days.</p>
      </div>
      <div class="segmented grid-cols-2" role="tablist" aria-label="Which reports">
        <button type="button" role="tab" class="segment px-5" [class.segment-on]="open()" [attr.aria-selected]="open()" (click)="show(true)">Open</button>
        <button type="button" role="tab" class="segment px-5" [class.segment-on]="!open()" [attr.aria-selected]="!open()" (click)="show(false)">Closed</button>
      </div>
    </header>

    @if (error()) { <p class="field-error mt-4" role="alert">{{ error() }}</p> }

    @if (reports(); as list) {
      @if (!list.length) {
        <div class="card mt-6 p-10 text-center">
          <app-icon name="checkCircle" [size]="32" class="text-brand-600" />
          <p class="mt-3 font-semibold">{{ open() ? 'No open reports.' : 'No closed reports yet.' }}</p>
        </div>
      } @else {
        <ul class="mt-6 space-y-4">
          @for (r of list; track r.id) {
            <li class="card p-5" [class]="r.overdue ? 'border-2 border-rose-500' : ''">
              <div class="flex flex-wrap items-center gap-2">
                <span class="badge badge-muted">{{ reasons[r.reason] }}</span>
                <span class="text-sm text-slate-600 dark:text-slate-400">about {{ what[r.targetType] }}</span>
                <span class="ml-auto flex flex-wrap items-center gap-2 text-sm">
                  @if (r.status === 'OPEN') {
                    @if (r.overdue) { <span class="badge badge-danger">Overdue</span> }
                    @else if (!r.acknowledgedAt) { <span class="badge badge-warning">Acknowledge by {{ r.acknowledgeBy | date: 'd MMM, h:mm a' }}</span> }
                    @else { <span class="badge badge-muted">Resolve by {{ r.resolveBy | date: 'd MMM' }}</span> }
                  } @else {
                    <span class="badge" [class]="r.status === 'ACTIONED' ? 'badge-success' : 'badge-muted'">{{ r.status === 'ACTIONED' ? 'Action taken' : 'Dismissed' }}</span>
                  }
                  <time class="text-slate-500" [title]="r.createdAt | date: 'd MMM y, h:mm a'">{{ ago(r.createdAt) }}</time>
                </span>
              </div>
              @if (r.snapshot) {
                <blockquote class="mt-3 rounded-2xl bg-slate-50 p-3 text-sm whitespace-pre-line dark:bg-slate-800/60">{{ r.snapshot }}</blockquote>
              }
              @if (r.details) { <p class="mt-2 text-sm"><b>Reporter says:</b> {{ r.details }}</p> }
              <div class="mt-4 grid gap-3 text-sm sm:grid-cols-2">
                <a [routerLink]="['/admin/members', r.reported.id]" class="flex items-center gap-2">
                  <app-avatar [url]="r.reported.avatarUrl" [name]="r.reported.displayName" [size]="32" />
                  <span><span class="block text-xs text-slate-500">Reported</span><span class="font-semibold">{{ r.reported.displayName }}</span>
                    <span class="text-xs text-slate-500"> · {{ r.reportsAboutMember }} {{ r.reportsAboutMember === 1 ? 'report' : 'reports' }} in total</span></span>
                </a>
                <a [routerLink]="['/admin/members', r.reporter.id]" class="flex items-center gap-2">
                  <app-avatar [url]="r.reporter.avatarUrl" [name]="r.reporter.displayName" [size]="32" />
                  <span><span class="block text-xs text-slate-500">Reported by</span><span class="font-semibold">{{ r.reporter.displayName }}</span></span>
                </a>
              </div>
              @if (r.rideId) { <a [routerLink]="['/admin/rides', r.rideId]" class="mt-3 inline-flex items-center gap-1 text-sm font-semibold text-brand-700 hover:underline dark:text-brand-400"><app-icon name="car" [size]="16" /> Open the ride</a> }

              @if (r.status === 'OPEN') {
                <div class="mt-4 border-t border-slate-100 pt-4 dark:border-slate-800">
                  @if (!r.acknowledgedAt) {
                    <button type="button" class="btn btn-secondary btn-sm mb-3" (click)="acknowledge(r)" [disabled]="busy()"><app-icon name="eye" [size]="16" /> Mark as seen</button>
                  }
                  <div class="flex flex-wrap items-end gap-2">
                    <label class="text-sm">
                      <span class="label text-xs">What did you do?</span>
                      <select class="input py-2 text-sm" [value]="actions()[r.id] ?? ''" (change)="setAction(r.id, $any($event.target).value)">
                        <option value="" disabled>Choose…</option>
                        @if (r.targetType === 'MESSAGE' || r.targetType === 'REVIEW') { <option value="REMOVE_CONTENT">Remove the {{ r.targetType === 'MESSAGE' ? 'message' : 'review' }}</option> }
                        <option value="ACTION_TAKEN">Took action (e.g. restricted the member)</option>
                        <option value="DISMISS">No rule broken — dismiss</option>
                      </select>
                    </label>
                    <label class="min-w-56 flex-1 text-sm">
                      <span class="label text-xs">Note for the record</span>
                      <input class="input py-2 text-sm" maxlength="500" [value]="notes()[r.id] ?? ''" (input)="setNote(r.id, $any($event.target).value)" placeholder="What you found and did" />
                    </label>
                    <button type="button" class="btn btn-primary btn-sm" (click)="resolve(r)" [disabled]="busy() || !actions()[r.id] || (notes()[r.id] ?? '').trim().length < 3">Close report</button>
                  </div>
                </div>
              } @else {
                <p class="mt-3 text-sm text-slate-600 dark:text-slate-400">{{ r.handledBy }}, {{ r.resolvedAt | date: 'd MMM, h:mm a' }}: “{{ r.resolution }}”</p>
              }
            </li>
          }
        </ul>
      }
    } @else if (!error()) {
      <div class="skeleton mt-6 h-40"></div>
    }
  `,
})
export class ReportsPage implements OnInit {
  private readonly api = inject(AdminApi);
  private readonly toast = inject(ToastService);

  protected readonly open = signal(true);
  protected readonly reports = signal<ReportRow[] | null>(null);
  protected readonly actions = signal<Record<string, Action>>({});
  protected readonly notes = signal<Record<string, string>>({});
  protected readonly busy = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly reasons = REASONS;
  protected readonly what = WHAT;
  protected readonly ago = timeAgo;

  ngOnInit(): void {
    void this.load();
  }

  protected show(open: boolean): void {
    this.open.set(open);
    this.reports.set(null);
    void this.load();
  }

  protected setAction(id: string, value: Action): void {
    this.actions.update((a) => ({ ...a, [id]: value }));
  }

  protected setNote(id: string, value: string): void {
    this.notes.update((n) => ({ ...n, [id]: value }));
  }

  protected async acknowledge(r: ReportRow): Promise<void> {
    await this.act(() => this.api.acknowledgeReport(r.id), 'Marked as seen.');
  }

  protected async resolve(r: ReportRow): Promise<void> {
    const action = this.actions()[r.id];
    if (!action) return;
    await this.act(() => this.api.resolveReport(r.id, action, (this.notes()[r.id] ?? '').trim()), 'Report closed.');
  }

  private async act(call: () => Promise<unknown>, done: string): Promise<void> {
    this.busy.set(true);
    try {
      await call();
      this.toast.success(done);
      await this.load();
    } catch (e) {
      this.toast.error(problemOf(e).detail);
    } finally {
      this.busy.set(false);
    }
  }

  private async load(): Promise<void> {
    try {
      this.reports.set(await this.api.reports(this.open()));
      this.error.set(null);
    } catch (e) {
      this.error.set(problemOf(e).detail);
    }
  }
}
