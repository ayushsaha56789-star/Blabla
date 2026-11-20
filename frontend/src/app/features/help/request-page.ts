import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, effect, inject, input, signal, untracked } from '@angular/core';
import { RouterLink } from '@angular/router';
import { problemOf } from '../../core/errors';
import { CATEGORIES, SupportApi, Ticket, ticketStatus } from '../../core/support-api';
import { ToastService } from '../../core/toast.service';
import { TripsService } from '../../core/trips.service';
import { Icon } from '../../shared/icon';

/** One help request: the conversation with support, a reply box, and closing it. */
@Component({
  selector: 'app-request-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [DatePipe, RouterLink, Icon],
  template: `
    <div class="container-page max-w-2xl py-8 sm:py-12">
      <a routerLink="/help" class="btn btn-ghost btn-sm -ml-2"><app-icon name="arrowLeft" [size]="16" /> Help</a>
      @if (error()) {
        <p class="card mt-4 p-6 text-center" role="alert">{{ error() }}</p>
      } @else if (ticket(); as t) {
        <header class="mt-3 flex flex-wrap items-start justify-between gap-3">
          <div>
            <p class="kicker">{{ categories[t.category] }}</p>
            <h1 class="mt-1 text-2xl font-extrabold tracking-tight">{{ t.subject }}</h1>
          </div>
          <span class="badge" [class]="'tone-' + status(t.status).tone">{{ status(t.status).text }}</span>
        </header>
        @if (t.status === 'OPEN' && !t.firstResponseAt) {
          <p class="mt-3 rounded-2xl bg-slate-50 p-3 text-sm text-slate-600 dark:bg-slate-800/60 dark:text-slate-400">Thanks for writing in. We'll reply here within 24 hours.</p>
        }
        <ol class="mt-6 space-y-3">
          @for (m of t.messages; track m.id) {
            <li class="flex" [class.justify-end]="!m.fromStaff">
              <div class="max-w-[85%] rounded-2xl px-4 py-3" [class]="m.fromStaff ? 'rounded-bl-md bg-white shadow-sm dark:bg-slate-800' : 'rounded-br-md bg-brand-600 text-white'">
                <p class="text-xs font-semibold" [class]="m.fromStaff ? 'text-brand-700 dark:text-brand-300' : 'text-brand-100'">{{ m.fromStaff ? m.author : 'You' }} · {{ m.createdAt | date: 'd MMM, h:mm a' }}</p>
                <p class="mt-1 whitespace-pre-line">{{ m.body }}</p>
              </div>
            </li>
          }
        </ol>
        <form class="card mt-6 p-4" (submit)="send($event)">
          <label for="r-body" class="label">{{ t.status === 'CLOSED' ? 'Need more help? Writing again reopens this request.' : 'Reply' }}</label>
          <textarea id="r-body" class="input min-h-24" maxlength="4000" [value]="reply()" (input)="reply.set($any($event.target).value)"></textarea>
          <div class="mt-3 flex flex-wrap justify-between gap-2">
            @if (t.status !== 'CLOSED') { <button type="button" class="btn btn-ghost btn-sm" (click)="close()" [disabled]="busy()">My problem is solved</button> } @else { <span></span> }
            <button type="submit" class="btn btn-primary btn-sm" [disabled]="busy() || !reply().trim()">Send</button>
          </div>
        </form>
        @if (t.status === 'ANSWERED') {
          <p class="mt-3 text-center text-xs text-slate-500 dark:text-slate-400">If we don't hear back within 7 days, we'll close this request. You can always write again to reopen it.</p>
        }
      } @else {
        <div class="card mt-4 p-8" aria-busy="true"><div class="skeleton h-6 w-1/2"></div><div class="skeleton mt-4 h-20"></div></div>
      }
    </div>
  `,
})
export class RequestPage {
  private readonly api = inject(SupportApi);
  private readonly toast = inject(ToastService);
  private readonly trips = inject(TripsService);

  readonly id = input.required<string>();

  protected readonly ticket = signal<Ticket | null>(null);
  protected readonly error = signal<string | null>(null);
  protected readonly reply = signal('');
  protected readonly busy = signal(false);
  protected readonly categories = CATEGORIES;
  protected readonly status = ticketStatus;

  constructor() {
    effect(() => {
      const id = this.id();
      untracked(() => void this.load(id));
    });
  }

  protected async send(event: Event): Promise<void> {
    event.preventDefault();
    await this.act(() => this.api.reply(this.id(), this.reply().trim()), null);
    this.reply.set('');
  }

  protected async close(): Promise<void> {
    await this.act(() => this.api.close(this.id()), 'Glad it\'s sorted! The request is closed.');
  }

  private async act(call: () => Promise<Ticket>, done: string | null): Promise<void> {
    this.busy.set(true);
    try {
      this.ticket.set(await call());
      if (done) this.toast.success(done);
    } catch (e) {
      this.toast.error(problemOf(e).detail);
    } finally {
      this.busy.set(false);
    }
  }

  private async load(id: string): Promise<void> {
    try {
      this.ticket.set(await this.api.get(id));
      void this.trips.refreshSupport();
    } catch (e) {
      this.error.set(problemOf(e).detail);
    }
  }
}
