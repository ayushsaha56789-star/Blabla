import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, inject, OnInit, signal } from '@angular/core';
import { Router } from '@angular/router';
import { problemOf } from '../../core/errors';
import { dayLabel, NoticeTopic, NoticeView, NotificationsApi, noticeIcon, TopicChoice } from '../../core/notifications-api';
import { ToastService } from '../../core/toast.service';
import { TripsService } from '../../core/trips.service';
import { safeReturnUrl } from '../../core/util';
import { Icon } from '../../shared/icon';

const TONE: Record<NoticeTopic, string> = {
  TRIPS: 'bg-brand-50 text-brand-700 dark:bg-brand-500/15 dark:text-brand-300',
  REMINDERS: 'bg-accent-100 text-accent-600 dark:bg-accent-600/15 dark:text-accent-300',
  ACCOUNT: 'bg-slate-100 text-slate-700 dark:bg-slate-800 dark:text-slate-300',
  REWARDS: 'bg-lime-100 text-lime-800 dark:bg-lime-500/15 dark:text-lime-200',
};

/** The inbox: what happened with bookings, trips, help requests and checks. Opening it marks everything as seen. */
@Component({
  selector: 'app-notifications-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [DatePipe, Icon],
  template: `
    <div class="container-page max-w-2xl py-8 sm:py-10">
      <p class="kicker">Inbox</p>
      <h1 class="mt-1 text-3xl font-extrabold tracking-tight">Notifications</h1>
      <p class="mt-1 text-slate-600 dark:text-slate-400">Bookings, trips, replies from our team and checks, all in one place.</p>

      @if (items(); as list) {
        @if (!list.length) {
          <div class="card mt-6 p-10 text-center">
            <app-icon name="bell" [size]="32" class="text-slate-400" />
            <p class="mt-3 font-semibold">Nothing yet.</p>
            <p class="mt-1 text-sm text-slate-600 dark:text-slate-400">We'll let you know here when someone books, answers or replies.</p>
          </div>
        } @else {
          @for (g of groups(); track g.day) {
            <h2 class="kicker mt-6 mb-2">{{ g.day }}</h2>
            <ul class="card divide-y divide-slate-100 overflow-hidden dark:divide-slate-800">
              @for (n of g.items; track n.id) {
                <li>
                  <button type="button" class="flex w-full items-start gap-3 p-4 text-left transition hover:bg-slate-50 focus-visible:bg-slate-50 focus-visible:outline-none dark:hover:bg-slate-900 dark:focus-visible:bg-slate-900"
                          [class]="n.read ? '' : 'bg-brand-50/70 dark:bg-brand-500/10'" (click)="open(n)">
                    <span class="grid h-10 w-10 shrink-0 place-items-center rounded-full" [class]="tone(n)"><app-icon [name]="icon(n)" [size]="20" /></span>
                    <span class="min-w-0 flex-1">
                      <span class="block" [class.font-bold]="!n.read" [class.font-semibold]="n.read">{{ n.title }}</span>
                      @if (n.body) { <span class="mt-0.5 block text-sm text-slate-600 dark:text-slate-400">{{ n.body }}</span> }
                      <span class="mt-1 block text-xs text-slate-500">{{ n.createdAt | date: 'h:mm a' }}</span>
                    </span>
                    @if (!n.read) { <span class="mt-2 h-2.5 w-2.5 shrink-0 rounded-full bg-accent-500" aria-label="New"></span> }
                  </button>
                </li>
              }
            </ul>
          }
          @if (more()) {
            <div class="mt-4 text-center"><button type="button" class="btn btn-secondary btn-sm" (click)="older()" [disabled]="loading()">Show older</button></div>
          }
        }
      } @else {
        <div class="skeleton mt-6 h-48"></div>
      }

      @if (topics(); as list) {
        <section class="card mt-8 p-5 sm:p-6" aria-labelledby="notice-settings">
          <h2 id="notice-settings" class="font-bold">What you get here</h2>
          <ul class="mt-3 space-y-3">
            @for (t of list; track t.topic) {
              <li class="flex items-center justify-between gap-4 text-sm">
                <span>{{ t.label }}</span>
                @if (t.canMute) {
                  <button type="button" role="switch" class="switch" [attr.aria-checked]="!t.muted" [attr.aria-label]="t.label" (click)="toggle(t)" [disabled]="saving()"><span class="switch-knob"></span></button>
                } @else {
                  <span class="text-xs font-semibold text-slate-500">Always on</span>
                }
              </li>
            }
          </ul>
          <p class="hint mt-3">Notices are deleted after 90 days.</p>
        </section>
      }
    </div>
  `,
})
export class NotificationsPage implements OnInit {
  private readonly api = inject(NotificationsApi);
  private readonly trips = inject(TripsService);
  private readonly router = inject(Router);
  private readonly toast = inject(ToastService);

  protected readonly items = signal<NoticeView[] | null>(null);
  protected readonly more = signal(false);
  protected readonly loading = signal(false);
  protected readonly topics = signal<TopicChoice[] | null>(null);
  protected readonly saving = signal(false);

  protected readonly groups = computed(() => {
    const out: { day: string; items: NoticeView[] }[] = [];
    for (const n of this.items() ?? []) {
      const day = dayLabel(n.createdAt);
      const last = out.at(-1);
      if (last?.day === day) last.items.push(n);
      else out.push({ day, items: [n] });
    }
    return out;
  });

  async ngOnInit(): Promise<void> {
    try {
      const inbox = await this.api.inbox();
      this.items.set(inbox.items);
      this.more.set(inbox.more);
      // Opening the inbox counts as seeing it; the new ones stay highlighted until the page is left.
      if (inbox.unread) {
        await this.api.markRead('all');
        this.trips.unreadNotices.set(0);
      }
    } catch (e) {
      this.toast.error(problemOf(e).detail);
      this.items.set([]);
    }
    this.topics.set((await this.api.settings().catch(() => null))?.topics ?? null);
  }

  protected icon(n: NoticeView) {
    return noticeIcon(n.icon);
  }

  protected tone(n: NoticeView): string {
    return TONE[n.topic] ?? TONE.ACCOUNT;
  }

  protected open(n: NoticeView): void {
    if (n.link) void this.router.navigateByUrl(safeReturnUrl(n.link, '/notifications'));
  }

  protected async older(): Promise<void> {
    const list = this.items() ?? [];
    if (!list.length) return;
    this.loading.set(true);
    try {
      const page = await this.api.inbox(list[list.length - 1].id);
      this.items.set([...list, ...page.items]);
      this.more.set(page.more);
    } catch (e) {
      this.toast.error(problemOf(e).detail);
    } finally {
      this.loading.set(false);
    }
  }

  protected async toggle(t: TopicChoice): Promise<void> {
    const muted = (this.topics() ?? []).filter((x) => (x.topic === t.topic ? !x.muted : x.muted)).map((x) => x.topic);
    this.saving.set(true);
    try {
      this.topics.set((await this.api.saveSettings(muted)).topics);
    } catch (e) {
      this.toast.error(problemOf(e).detail);
    } finally {
      this.saving.set(false);
    }
  }
}
