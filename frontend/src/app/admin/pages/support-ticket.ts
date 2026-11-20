import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, effect, inject, input, signal, untracked } from '@angular/core';
import { RouterLink } from '@angular/router';
import { problemOf } from '../../core/errors';
import { ToastService } from '../../core/toast.service';
import { Avatar } from '../../shared/avatar';
import { Icon } from '../../shared/icon';
import { AdminApi } from '../admin-api';
import { StaffTicketView } from '../admin.models';
import { TICKET_STATUS, TOPIC } from './support';

/** One help request: the member, the conversation, a reply box. */
@Component({
  selector: 'app-admin-support-ticket',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [DatePipe, RouterLink, Avatar, Icon],
  template: `
    <a routerLink="/admin/support" class="btn btn-ghost btn-sm -ml-2"><app-icon name="arrowLeft" [size]="16" /> Help requests</a>
    @if (error()) {
      <p class="card mt-4 p-6 text-center" role="alert">{{ error() }}</p>
    } @else if (view(); as v) {
      <div class="mt-3 grid gap-6 lg:grid-cols-[1fr_18rem]">
        <section class="min-w-0">
          <header class="flex flex-wrap items-start justify-between gap-3">
            <div class="min-w-0">
              <p class="kicker">{{ topic[v.ticket.category] }}</p>
              <h1 class="mt-1 text-2xl font-extrabold tracking-tight break-words">{{ v.ticket.subject }}</h1>
            </div>
            @if (v.ticket.overdue) { <span class="badge badge-danger">Overdue</span> }
            @else { <span class="badge" [class]="'tone-' + status[v.ticket.status].tone">{{ status[v.ticket.status].text }}</span> }
          </header>
          @if (v.ticket.category === 'GRIEVANCE') {
            <p class="mt-3 rounded-2xl bg-accent-100 p-3 text-sm text-accent-600 dark:bg-accent-600/15 dark:text-accent-300">
              A formal grievance: acknowledge it within 24 hours and resolve it within 15 days ({{ resolveBy(v.ticket.createdAt) | date: 'd MMM' }}).
            </p>
          }
          <ol class="mt-6 space-y-3">
            @for (m of v.messages; track m.id) {
              <li class="flex" [class.justify-end]="m.fromStaff">
                <div class="max-w-[85%] rounded-2xl px-4 py-3" [class]="m.fromStaff ? 'rounded-br-md bg-brand-600 text-white' : 'rounded-bl-md bg-white shadow-sm dark:bg-slate-800'">
                  <p class="text-xs font-semibold" [class]="m.fromStaff ? 'text-brand-100' : 'text-slate-500 dark:text-slate-400'">{{ m.author }} · {{ m.createdAt | date: 'd MMM, h:mm a' }}</p>
                  <p class="mt-1 whitespace-pre-line break-words">{{ m.body }}</p>
                </div>
              </li>
            }
          </ol>
          <form class="card mt-6 p-4" (submit)="send($event)">
            <label for="t-reply" class="label">Reply to {{ v.ticket.member.displayName }}</label>
            <textarea id="t-reply" class="input min-h-28" maxlength="4000" [value]="reply()" (input)="reply.set($any($event.target).value)"
                      placeholder="Be kind and specific. The member sees your first name."></textarea>
            <div class="mt-3 flex flex-wrap justify-between gap-2">
              @if (v.ticket.status !== 'CLOSED') {
                <button type="button" class="btn btn-ghost btn-sm" (click)="close()" [disabled]="busy()">Close without replying</button>
              } @else { <span></span> }
              <button type="submit" class="btn btn-primary btn-sm" [disabled]="busy() || !reply().trim()">Send reply</button>
            </div>
          </form>
        </section>

        <aside class="space-y-4">
          <div class="card p-4">
            <p class="kicker">Member</p>
            <a [routerLink]="['/admin/members', v.ticket.member.id]" class="mt-3 flex items-center gap-3">
              <app-avatar [url]="v.ticket.member.avatarUrl" [name]="v.ticket.member.displayName" [size]="44" />
              <span class="min-w-0">
                <span class="block truncate font-semibold">{{ v.ticket.member.displayName }}</span>
                @if (v.maskedPhone) { <span class="block text-sm text-slate-500 dark:text-slate-400">{{ v.maskedPhone }}</span> }
              </span>
            </a>
          </div>
          <dl class="card space-y-3 p-4 text-sm">
            <div><dt class="text-slate-500 dark:text-slate-400">Sent</dt><dd class="font-medium">{{ v.ticket.createdAt | date: 'd MMM y, h:mm a' }}</dd></div>
            <div><dt class="text-slate-500 dark:text-slate-400">First reply</dt>
              <dd class="font-medium">{{ v.ticket.firstResponseAt ? (v.ticket.firstResponseAt | date: 'd MMM, h:mm a') : 'Not yet, due ' + (v.ticket.respondBy | date: 'd MMM, h:mm a') }}</dd></div>
            @if (v.rideId) {
              <div><dt class="text-slate-500 dark:text-slate-400">About a ride</dt>
                <dd><a [routerLink]="['/admin/rides', v.rideId]" class="font-semibold text-brand-700 hover:underline dark:text-brand-400">Open the ride</a></dd></div>
            }
          </dl>
        </aside>
      </div>
    } @else {
      <div class="skeleton mt-4 h-60"></div>
    }
  `,
})
export class SupportTicketPage {
  private readonly api = inject(AdminApi);
  private readonly toast = inject(ToastService);

  readonly id = input.required<string>();

  protected readonly view = signal<StaffTicketView | null>(null);
  protected readonly error = signal<string | null>(null);
  protected readonly reply = signal('');
  protected readonly busy = signal(false);
  protected readonly topic = TOPIC;
  protected readonly status = TICKET_STATUS;

  constructor() {
    effect(() => {
      const id = this.id();
      untracked(() => void this.load(id));
    });
  }

  protected resolveBy(createdAt: string): Date {
    return new Date(new Date(createdAt).getTime() + 15 * 24 * 3600 * 1000);
  }

  protected async send(event: Event): Promise<void> {
    event.preventDefault();
    const body = this.reply().trim();
    if (!body) return;
    if (await this.act(() => this.api.replyToTicket(this.id(), body), 'Reply sent.')) this.reply.set('');
  }

  protected async close(): Promise<void> {
    await this.act(() => this.api.closeTicket(this.id()), 'Request closed.');
  }

  private async act(call: () => Promise<StaffTicketView>, done: string): Promise<boolean> {
    this.busy.set(true);
    try {
      this.view.set(await call());
      this.toast.success(done);
      return true;
    } catch (e) {
      this.toast.error(problemOf(e).detail);
      return false;
    } finally {
      this.busy.set(false);
    }
  }

  private async load(id: string): Promise<void> {
    try {
      this.view.set(await this.api.ticket(id));
    } catch (e) {
      this.error.set(problemOf(e).detail);
    }
  }
}
