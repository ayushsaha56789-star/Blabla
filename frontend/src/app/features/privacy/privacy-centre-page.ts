import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, inject, OnInit, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { AuthService } from '../../core/auth.service';
import { problemOf } from '../../core/errors';
import { ModerationApi } from '../../core/moderation-api';
import { LEAVE_REASONS, LeaveReason, PrivacyApi, saveFile } from '../../core/privacy-api';
import { MemberCard } from '../../core/rides.models';
import { ToastService } from '../../core/toast.service';
import { Avatar } from '../../shared/avatar';
import { Icon } from '../../shared/icon';

/** The privacy centre: what others see, a copy of your data, blocked members, and deleting your account. */
@Component({
  selector: 'app-privacy-centre-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [DatePipe, RouterLink, Avatar, Icon],
  template: `
    <div class="container-page max-w-3xl py-8 sm:py-12">
      <header>
        <p class="kicker">Your data, your choice</p>
        <h1 class="mt-1 text-3xl font-extrabold tracking-tight sm:text-4xl">Privacy centre</h1>
        <p class="mt-2 text-slate-600 dark:text-slate-400">What other members see, a copy of everything we keep, and how to leave if you want to.</p>
      </header>

      <section class="card mt-8 p-6" aria-labelledby="see-heading">
        <div class="flex items-start gap-4">
          <span class="flex h-11 w-11 shrink-0 items-center justify-center rounded-2xl bg-brand-50 text-brand-700 dark:bg-brand-500/15 dark:text-brand-300"><app-icon name="eye" [size]="22" /></span>
          <div class="min-w-0 flex-1">
            <h2 id="see-heading" class="text-lg font-bold">What other members see</h2>
            <ul class="mt-3 space-y-2 text-sm text-slate-600 dark:text-slate-400">
              <li class="flex gap-2"><app-icon name="check" [size]="18" class="mt-0.5 shrink-0 text-brand-600" /> Your first name and the first letter of your last name, your photo, bio, city, age, travel preferences and reviews.</li>
              <li class="flex gap-2"><app-icon name="phone" [size]="18" class="mt-0.5 shrink-0 text-brand-600" /> Your phone number only goes to the people on the same ride, and only once a seat is confirmed.</li>
              <li class="flex gap-2"><app-icon name="lock" [size]="18" class="mt-0.5 shrink-0 text-brand-600" /> Never shown: your email, your date of birth, your number plate (until a seat is confirmed) and your emergency contacts.</li>
            </ul>
            @if (auth.user(); as me) {
              <a [routerLink]="['/u', me.id]" class="btn btn-secondary btn-sm mt-4">See my public profile <app-icon name="arrowRight" [size]="16" /></a>
            }
          </div>
        </div>
      </section>

      <section class="card mt-4 p-6" aria-labelledby="download-heading">
        <div class="flex items-start gap-4">
          <span class="flex h-11 w-11 shrink-0 items-center justify-center rounded-2xl bg-sky-50 text-sky-700 dark:bg-sky-500/15 dark:text-sky-300"><app-icon name="download" [size]="22" /></span>
          <div class="min-w-0 flex-1">
            <h2 id="download-heading" class="text-lg font-bold">Download my data</h2>
            <p class="mt-1 text-sm text-slate-600 dark:text-slate-400">One file with everything SaathChalo keeps about you: your profile, cars, rides and bookings, chats, reviews, safety contacts and help requests.</p>
            <button type="button" class="btn btn-secondary btn-sm mt-4" (click)="download()" [disabled]="downloading()">
              <app-icon name="download" [size]="16" /> {{ downloading() ? 'Preparing…' : 'Download (JSON file)' }}
            </button>
          </div>
        </div>
      </section>

      <section class="card mt-4 p-6" aria-labelledby="blocked-heading">
        <div class="flex items-start gap-4">
          <span class="flex h-11 w-11 shrink-0 items-center justify-center rounded-2xl bg-slate-100 text-slate-700 dark:bg-slate-800 dark:text-slate-300"><app-icon name="ban" [size]="22" /></span>
          <div class="min-w-0 flex-1">
            <h2 id="blocked-heading" class="text-lg font-bold">Blocked members</h2>
            <p class="mt-1 text-sm text-slate-600 dark:text-slate-400">You and the people you block can't message each other or book each other's rides.</p>
            @if (blocks(); as list) {
              @if (!list.length) {
                <p class="mt-4 text-sm font-medium">You haven't blocked anyone.</p>
              } @else {
                <ul class="mt-4 divide-y divide-slate-100 dark:divide-slate-800">
                  @for (m of list; track m.id) {
                    <li class="flex items-center gap-3 py-3">
                      <app-avatar [url]="m.avatarUrl" [name]="m.displayName" [size]="36" />
                      <span class="min-w-0 flex-1 truncate font-semibold">{{ m.displayName }}</span>
                      <button type="button" class="btn btn-ghost btn-sm" (click)="unblock(m)" [disabled]="busy()">Unblock</button>
                    </li>
                  }
                </ul>
              }
            } @else {
              <div class="skeleton mt-4 h-10"></div>
            }
          </div>
        </div>
      </section>

      <nav class="mt-4 grid gap-3 sm:grid-cols-2" aria-label="Our policies">
        @for (l of legal; track l.path) {
          <a [routerLink]="l.path" class="card flex items-center gap-3 p-4 transition hover:shadow-md">
            <app-icon [name]="l.icon" [size]="20" class="text-brand-600 dark:text-brand-400" />
            <span class="flex-1 font-semibold">{{ l.label }}</span>
            <app-icon name="arrowRight" [size]="16" class="text-slate-400" />
          </a>
        }
      </nav>

      <section class="card mt-8 border border-rose-200 p-6 dark:border-rose-500/30" aria-labelledby="delete-heading">
        <h2 id="delete-heading" class="text-lg font-bold text-rose-700 dark:text-rose-300">Delete my account</h2>
        <ul class="mt-3 space-y-2 text-sm text-slate-600 dark:text-slate-400">
          <li>• Your account stops working straight away and your profile is hidden.</li>
          <li>• Rides you offered that nobody is confirmed on are cancelled.</li>
          <li>• After 30 days we erase your details. <b class="text-slate-800 dark:text-slate-200">Changed your mind? Just log in again before then.</b></li>
          <li>• Past trips stay in your co-travellers' history as "Former member". Indian law asks us to keep your sign-up details, locked away, for 180 days; then they are deleted too.</li>
        </ul>
        @if (deleteError(); as message) {
          <div class="mt-4 rounded-2xl bg-rose-50 p-4 text-sm text-rose-800 dark:bg-rose-500/10 dark:text-rose-200" role="alert">
            <p>{{ message }}</p>
            @if (blockedByTrips()) { <a routerLink="/my-rides" class="mt-2 inline-flex font-semibold underline">Go to My rides</a> }
          </div>
        }
        @if (!confirming()) {
          <button type="button" class="btn btn-sm mt-5 border border-rose-300 text-rose-700 hover:bg-rose-50 dark:border-rose-500/40 dark:text-rose-300 dark:hover:bg-rose-500/10" (click)="confirming.set(true)">
            <app-icon name="trash" [size]="16" /> Delete my account
          </button>
        } @else {
          <div class="mt-5 space-y-4 rounded-2xl bg-slate-50 p-4 dark:bg-slate-800/50">
            <div>
              <label for="leave-reason" class="label">Would you tell us why? (optional)</label>
              <select id="leave-reason" class="input" [value]="reason() ?? ''" (change)="reason.set($any($event.target).value || null)">
                <option value="">I'd rather not say</option>
                @for (r of reasonKeys; track r) { <option [value]="r">{{ reasons[r] }}</option> }
              </select>
            </div>
            <label class="flex items-start gap-3 text-sm">
              <input type="checkbox" class="mt-1 h-4 w-4 accent-rose-600" [checked]="understood()" (change)="understood.set($any($event.target).checked)" />
              <span>I understand that my account will be erased on {{ dueDate() | date: 'd MMMM y' }} unless I log in again before then.</span>
            </label>
            <div class="flex flex-wrap justify-end gap-2">
              <button type="button" class="btn btn-ghost btn-sm" (click)="cancelConfirm()">Keep my account</button>
              <button type="button" class="btn btn-sm bg-rose-600 text-white hover:bg-rose-700" (click)="deleteAccount()" [disabled]="!understood() || busy()">
                {{ busy() ? 'Deleting…' : 'Yes, delete my account' }}
              </button>
            </div>
          </div>
        }
      </section>
    </div>
  `,
})
export class PrivacyCentrePage implements OnInit {
  private readonly api = inject(PrivacyApi);
  private readonly moderation = inject(ModerationApi);
  private readonly toast = inject(ToastService);
  protected readonly auth = inject(AuthService);

  protected readonly legal = [
    { path: '/legal/privacy', label: 'Privacy policy', icon: 'lock' as const },
    { path: '/legal/terms', label: 'Terms of use', icon: 'info' as const },
    { path: '/legal/community', label: 'Community guidelines', icon: 'users' as const },
    { path: '/legal/grievance', label: 'Grievance officer', icon: 'flag' as const },
  ];
  protected readonly reasons = LEAVE_REASONS;
  protected readonly reasonKeys = Object.keys(LEAVE_REASONS) as LeaveReason[];

  protected readonly blocks = signal<MemberCard[] | null>(null);
  protected readonly downloading = signal(false);
  protected readonly busy = signal(false);
  protected readonly confirming = signal(false);
  protected readonly understood = signal(false);
  protected readonly reason = signal<LeaveReason | null>(null);
  protected readonly deleteError = signal<string | null>(null);
  protected readonly blockedByTrips = signal(false);

  async ngOnInit(): Promise<void> {
    try {
      this.blocks.set(await this.moderation.myBlocks());
    } catch {
      this.blocks.set([]);
    }
  }

  protected dueDate(): Date {
    return new Date(Date.now() + 30 * 24 * 3600 * 1000);
  }

  protected async download(): Promise<void> {
    this.downloading.set(true);
    try {
      const blob = await this.api.exportData();
      saveFile(blob, `saathchalo-my-data-${new Date().toISOString().slice(0, 10)}.json`);
    } catch (e) {
      this.toast.error(await blobProblem(e));
    } finally {
      this.downloading.set(false);
    }
  }

  protected async unblock(m: MemberCard): Promise<void> {
    if (!m.id) return;
    this.busy.set(true);
    try {
      await this.moderation.unblock(m.id);
      this.blocks.update((list) => (list ?? []).filter((b) => b.id !== m.id));
      this.toast.success(`You unblocked ${m.displayName}.`);
    } catch (e) {
      this.toast.error(problemOf(e).detail);
    } finally {
      this.busy.set(false);
    }
  }

  protected cancelConfirm(): void {
    this.confirming.set(false);
    this.understood.set(false);
    this.deleteError.set(null);
  }

  protected async deleteAccount(): Promise<void> {
    this.busy.set(true);
    this.deleteError.set(null);
    try {
      const { deleteOn } = await this.api.deleteAccount(this.reason());
      const when = new Date(deleteOn).toLocaleDateString('en-IN', { day: 'numeric', month: 'long', year: 'numeric' });
      await this.auth.logout();
      this.toast.success(`Your account will be erased on ${when}. Changed your mind? Just log in again before then.`);
    } catch (e) {
      const p = problemOf(e);
      this.blockedByTrips.set(p.code === 'upcoming_trips');
      this.deleteError.set(p.detail);
    } finally {
      this.busy.set(false);
    }
  }
}

/** Errors for blob requests arrive as a Blob body; read it so the member sees the real message. */
async function blobProblem(e: unknown): Promise<string> {
  const err = e as { error?: unknown; status?: number };
  if (err?.error instanceof Blob) {
    try {
      const body = JSON.parse(await err.error.text());
      if (typeof body?.detail === 'string' && (err.status ?? 500) < 500) return body.detail;
    } catch {
      // Fall through to the general message.
    }
  }
  return problemOf(e).detail;
}
