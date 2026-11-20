import { ChangeDetectionStrategy, Component, computed, inject, OnInit, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { AuthService } from '../../core/auth.service';
import { problemOf } from '../../core/errors';
import { FeaturesService } from '../../core/features.service';
import { rupees } from '../../core/rides-format';
import { BADGE_ICON, BoardRow, impactCard, levelProgress, RewardsView, SocialApi } from '../../core/social-api';
import { ToastService } from '../../core/toast.service';
import { Icon } from '../../shared/icon';

/** "My journey": level and XP, impact, streak, the monthly challenge, badges, invites and the eco leaderboard. */
@Component({
  selector: 'app-rewards-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, Icon],
  template: `
    <div class="container-page max-w-4xl py-8 sm:py-10">
      <p class="kicker">Rewards</p>
      <h1 class="mt-1 text-3xl font-extrabold tracking-tight">My journey</h1>
      @if (error()) {
        <p class="card mt-6 p-6 text-center" role="alert">{{ error() }}</p>
      } @else if (view(); as r) {
        <section class="mt-6 overflow-hidden rounded-3xl bg-linear-to-br from-amber-400 via-orange-500 to-rose-500 p-6 text-white shadow-xl sm:p-8">
          <div class="flex flex-wrap items-center gap-5">
            <span class="flex h-20 w-20 shrink-0 flex-col items-center justify-center rounded-full bg-white/20 ring-4 ring-white/40">
              <span class="text-xs font-semibold uppercase">Level</span><span class="text-3xl leading-none font-extrabold">{{ r.level }}</span>
            </span>
            <div class="min-w-[12rem] flex-1">
              <p class="text-2xl font-extrabold">{{ r.levelName }}</p>
              <p class="text-white/90">{{ r.xp.toLocaleString('en-IN') }} XP</p>
              <div class="mt-2 h-3 overflow-hidden rounded-full bg-white/25" role="progressbar" [attr.aria-valuenow]="progress()" aria-valuemin="0" aria-valuemax="100" aria-label="Progress to the next level">
                <div class="h-full rounded-full bg-white transition-all" [style.width.%]="progress()"></div>
              </div>
              <p class="mt-1 text-sm text-white/90">@if (r.nextLevelXp) { {{ (r.nextLevelXp - r.xp).toLocaleString('en-IN') }} XP to Level {{ r.level + 1 }}: {{ r.nextLevelName }} } @else { Top level reached! }</p>
            </div>
            <button type="button" class="btn w-full bg-white text-orange-600 hover:bg-orange-50 sm:w-auto" (click)="share(r)" [disabled]="busy()"><app-icon name="external" [size]="18" /> Share my card</button>
          </div>
        </section>

        <section class="mt-5 grid grid-cols-2 gap-3 sm:grid-cols-5" aria-label="Your impact">
          @for (t of tiles(); track t.label) {
            <div class="card p-4 text-center"><p class="text-2xl font-extrabold">{{ t.value }}</p><p class="text-xs text-slate-500 dark:text-slate-400">{{ t.label }}</p></div>
          }
        </section>
        <p class="hint">CO₂ saved: every passenger-km on a shared ride is a car-km not driven (about 120 g of CO₂ for an average car).</p>

        <div class="mt-5 grid gap-4 sm:grid-cols-2">
          <section class="card p-5" aria-labelledby="streak-heading">
            <h2 id="streak-heading" class="kicker">Streak</h2>
            <p class="mt-2 text-3xl font-extrabold">🔥 {{ r.streak }} {{ r.streak === 1 ? 'week' : 'weeks' }}</p>
            <p class="text-sm text-slate-600 dark:text-slate-400">Share a ride every week to keep it going. Best: {{ r.bestStreak }}.</p>
          </section>
          <section class="card p-5" aria-labelledby="challenge-heading">
            <h2 id="challenge-heading" class="kicker">This month's challenge</h2>
            <p class="mt-2 font-bold">{{ r.challenge.title }} @if (r.challenge.complete) { <span class="badge badge-success ml-1">Done! +100 XP</span> }</p>
            <div class="mt-2 flex gap-2">
              @for (i of steps(); track i) { <span class="h-3 flex-1 rounded-full" [class]="i < r.challenge.done ? 'bg-brand-500' : 'bg-slate-200 dark:bg-slate-700'"></span> }
            </div>
            <p class="mt-1 text-sm text-slate-600 dark:text-slate-400">{{ r.challenge.done }} of {{ r.challenge.goal }}</p>
          </section>
        </div>

        <section class="mt-6" aria-labelledby="badges-heading">
          <h2 id="badges-heading" class="kicker mb-3">Badges · {{ earnedCount() }} of {{ r.badges.length }}</h2>
          <ul class="grid grid-cols-2 gap-3 sm:grid-cols-5">
            @for (b of r.badges; track b.key) {
              <li class="card p-4 text-center" [class.opacity-60]="!b.earned">
                <p class="text-3xl" [class.grayscale]="!b.earned" aria-hidden="true">{{ icon(b.key) }}</p>
                <p class="mt-1 text-sm font-bold">{{ b.name }}</p>
                <p class="text-xs text-slate-500 dark:text-slate-400">{{ b.how }}</p>
                @if (!b.earned && b.goal > 1) {
                  <div class="mt-2 h-1.5 overflow-hidden rounded-full bg-slate-200 dark:bg-slate-700"><div class="h-full bg-brand-500" [style.width.%]="(b.progress / b.goal) * 100"></div></div>
                }
              </li>
            }
          </ul>
        </section>

        @if (features.isOn('rewards.referrals')) {
          <section class="card mt-6 p-5 sm:p-6" aria-labelledby="invite-heading">
            <h2 id="invite-heading" class="text-lg font-bold">Invite friends, ride for less</h2>
            <p class="mt-1 text-sm text-slate-600 dark:text-slate-400">When a friend joins with your code and completes their first ride, you both get {{ money(r.referral.creditRupees) }} in SaathChalo credits.</p>
            <div class="mt-4 flex flex-wrap items-center gap-3">
              <span class="rounded-2xl bg-slate-100 px-4 py-2 font-mono text-xl font-extrabold tracking-widest dark:bg-slate-800">{{ r.referral.code }}</span>
              <button type="button" class="btn btn-secondary btn-sm" (click)="copyInvite(r.referral.code)"><app-icon name="copy" [size]="16" /> Copy invite link</button>
              <span class="text-sm text-slate-600 dark:text-slate-400">{{ r.referral.invited }} joined · {{ r.referral.rewarded }} rewarded</span>
            </div>
            @if (r.referral.canClaim) {
              <form class="mt-4 flex max-w-sm gap-2" (submit)="claim($event)">
                <label class="sr-only" for="invite-code">A friend's invite code</label>
                <input id="invite-code" class="input flex-1 font-mono uppercase" maxlength="12" placeholder="Friend's code" [value]="code()" (input)="code.set($any($event.target).value)" />
                <button type="submit" class="btn btn-primary" [disabled]="busy() || code().trim().length < 5">Use code</button>
              </form>
            } @else if (r.referral.invitedByFriend) {
              <p class="mt-3 text-sm">You joined with a friend's code: @if (r.referral.status === 'REWARDED') { your welcome credits are in your wallet. } @else { complete your first ride to get your welcome credits. }</p>
            }
          </section>
        }

        <section class="card mt-6 p-5 sm:p-6" aria-labelledby="board-heading">
          <div class="flex flex-wrap items-center justify-between gap-3">
            <div>
              <h2 id="board-heading" class="text-lg font-bold">Eco champions{{ month() ? ' · ' + month() : '' }}</h2>
              <p class="text-sm text-slate-600 dark:text-slate-400">Opt in to appear by nickname. Your name and photo are never shown.</p>
            </div>
            <button type="button" role="switch" class="switch" [attr.aria-checked]="r.board.on" aria-label="Show me on the eco leaderboard" (click)="toggleBoard(r)" [disabled]="busy()"><span class="switch-knob"></span></button>
          </div>
          @if (editingNickname() || (r.board.on && !r.board.nickname)) {
            <form class="mt-3 flex max-w-sm gap-2" (submit)="saveNickname($event)">
              <label class="sr-only" for="nickname">Nickname</label>
              <input id="nickname" class="input flex-1" maxlength="24" placeholder="A nickname, e.g. Green Rider" [value]="nickname()" (input)="nickname.set($any($event.target).value)" />
              <button type="submit" class="btn btn-primary" [disabled]="busy() || nickname().trim().length < 3">Save</button>
            </form>
          } @else if (r.board.on) {
            <p class="mt-2 text-sm">You appear as <b>{{ r.board.nickname }}</b>. <button type="button" class="font-semibold text-brand-700 underline dark:text-brand-400" (click)="editingNickname.set(true)">Change</button></p>
          }
          @if (board(); as rows) {
            @if (rows.length) {
              <ol class="mt-4 divide-y divide-slate-100 dark:divide-slate-800">
                @for (row of rows; track row.rank) {
                  <li class="flex items-center gap-3 py-2" [class]="row.you ? 'font-bold text-brand-700 dark:text-brand-300' : ''">
                    <span class="w-6 text-center text-slate-500">{{ row.rank }}</span>
                    <span class="flex-1 truncate">{{ row.nickname }}</span>
                    <span class="text-sm">{{ row.co2Kg }} kg CO₂</span>
                  </li>
                }
              </ol>
            } @else { <p class="mt-4 text-sm text-slate-500 dark:text-slate-400">Nobody on the board yet this month. Be the first!</p> }
          }
        </section>
        @if (!r.impact.trips) {
          <p class="mt-6 text-center text-sm text-slate-600 dark:text-slate-400">Your first shared ride starts the fun. <a routerLink="/" class="font-semibold text-brand-700 underline dark:text-brand-400">Find a ride</a></p>
        }
      } @else {
        <div class="skeleton mt-6 h-48"></div>
      }
    </div>
  `,
})
export class RewardsPage implements OnInit {
  private readonly api = inject(SocialApi);
  private readonly toast = inject(ToastService);
  private readonly auth = inject(AuthService);
  protected readonly features = inject(FeaturesService);

  protected readonly view = signal<RewardsView | null>(null);
  protected readonly board = signal<BoardRow[] | null>(null);
  protected readonly month = signal('');
  protected readonly error = signal<string | null>(null);
  protected readonly busy = signal(false);
  protected readonly code = signal('');
  protected readonly nickname = signal('');
  protected readonly editingNickname = signal(false);
  protected readonly money = rupees;

  protected readonly progress = computed(() => {
    const r = this.view();
    return r ? levelProgress(r) : 0;
  });
  protected readonly earnedCount = computed(() => this.view()?.badges.filter((b) => b.earned).length ?? 0);
  protected readonly steps = computed(() => Array.from({ length: this.view()?.challenge.goal ?? 0 }, (_, i) => i));
  protected readonly tiles = computed(() => {
    const i = this.view()?.impact;
    if (!i) return [];
    return [
      { value: String(i.trips), label: i.trips === 1 ? 'ride shared' : 'rides shared' },
      { value: i.km.toLocaleString('en-IN'), label: 'km shared' },
      { value: `${i.co2Kg} kg`, label: 'CO₂ saved' },
      { value: String(i.people), label: i.people === 1 ? 'person met' : 'people met' },
      { value: rupees(i.rupees), label: 'costs shared' },
    ];
  });

  async ngOnInit(): Promise<void> {
    try {
      const r = await this.api.rewards();
      this.view.set(r);
      this.nickname.set(r.board.nickname ?? '');
      await this.loadBoard();
    } catch (e) {
      this.error.set(problemOf(e).detail);
    }
  }

  protected icon(key: string): string {
    return BADGE_ICON[key] ?? '🏅';
  }

  protected async share(r: RewardsView): Promise<void> {
    this.busy.set(true);
    try {
      const blob = await impactCard(r, this.auth.user()?.firstName ?? 'Me');
      const file = new File([blob], 'my-saathchalo-journey.png', { type: 'image/png' });
      if (navigator.canShare?.({ files: [file] })) {
        await navigator.share({ files: [file], title: 'My SaathChalo journey' });
      } else {
        const url = URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = file.name;
        a.click();
        setTimeout(() => URL.revokeObjectURL(url), 5000);
        this.toast.success('Your card is saved. Share it anywhere!');
      }
    } catch (e) {
      if ((e as Error)?.name !== 'AbortError') this.toast.error('Could not make the card on this device.');
    } finally {
      this.busy.set(false);
    }
  }

  protected async copyInvite(code: string): Promise<void> {
    const link = `${location.origin}/invite/${code}`;
    try {
      await navigator.clipboard.writeText(link);
      this.toast.success('Invite link copied.');
    } catch {
      this.toast.info(link);
    }
  }

  protected async claim(event: Event): Promise<void> {
    event.preventDefault();
    await this.run(() => this.api.claim(this.code().trim()), 'Code added! Take your first ride to get your welcome credits.');
  }

  protected async toggleBoard(r: RewardsView): Promise<void> {
    if (!r.board.on && !r.board.nickname) {
      this.editingNickname.set(true);
      return;
    }
    await this.run(() => this.api.board(!r.board.on, r.board.nickname), r.board.on ? 'You’re off the leaderboard.' : 'You’re on the leaderboard.');
  }

  protected async saveNickname(event: Event): Promise<void> {
    event.preventDefault();
    const ok = await this.run(() => this.api.board(true, this.nickname().trim()), 'You’re on the leaderboard.');
    if (ok) this.editingNickname.set(false);
  }

  private async run(call: () => Promise<RewardsView>, done: string): Promise<boolean> {
    this.busy.set(true);
    try {
      this.view.set(await call());
      this.toast.success(done);
      await this.loadBoard();
      return true;
    } catch (e) {
      const p = problemOf(e);
      this.toast.error(p.errors ? Object.values(p.errors)[0] : p.detail);
      return false;
    } finally {
      this.busy.set(false);
    }
  }

  private async loadBoard(): Promise<void> {
    try {
      const b = await this.api.leaderboard();
      this.board.set(b.top);
      this.month.set(b.month);
    } catch {
      this.board.set([]);
    }
  }
}
