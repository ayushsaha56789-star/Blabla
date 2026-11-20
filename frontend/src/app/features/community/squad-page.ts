import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, effect, inject, input, signal, untracked } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { problemOf } from '../../core/errors';
import { MemberCard } from '../../core/rides.models';
import { RideCard, SocialApi, SquadCard } from '../../core/social-api';
import { ToastService } from '../../core/toast.service';
import { Avatar } from '../../shared/avatar';
import { Icon } from '../../shared/icon';

/** One squad: its members, the rides they're offering, and the code to invite others. */
@Component({
  selector: 'app-squad-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [DatePipe, RouterLink, Avatar, Icon],
  template: `
    <div class="container-page max-w-3xl py-6 sm:py-10">
      <a routerLink="/community" class="btn btn-ghost btn-sm -ml-2"><app-icon name="arrowLeft" [size]="16" /> Squads &amp; events</a>
      @if (error()) {
        <p class="card mt-4 p-6 text-center" role="alert">{{ error() }}</p>
      } @else if (data(); as d) {
        <header class="card mt-3 p-5 sm:p-6">
          <p class="kicker">{{ d.squad.kind === 'COLLEGE' ? 'College squad' : d.squad.kind === 'OFFICE' ? 'Office squad' : 'Friends squad' }}</p>
          <h1 class="mt-1 text-2xl font-extrabold">{{ d.squad.name }}</h1>
          @if (d.squad.emailDomain) { <p class="text-sm text-slate-600 dark:text-slate-400">Only people with a confirmed &#64;{{ d.squad.emailDomain }} email can join.</p> }
          <div class="mt-4 flex flex-wrap items-center gap-3">
            <span class="rounded-2xl bg-slate-100 px-4 py-2 font-mono text-xl font-extrabold tracking-widest dark:bg-slate-800">{{ d.squad.code }}</span>
            <button type="button" class="btn btn-secondary btn-sm" (click)="copy(d.squad)"><app-icon name="copy" [size]="16" /> Copy the code</button>
          </div>
        </header>

        <section class="mt-5" aria-labelledby="rides-heading">
          <h2 id="rides-heading" class="kicker mb-2">Rides from your squad</h2>
          @if (d.rides.length) {
            <ul class="space-y-2">
              @for (r of d.rides; track r.id) {
                <li>
                  <a [routerLink]="['/rides', r.id]" class="card flex items-center gap-3 p-4 transition hover:shadow-md">
                    <app-avatar [url]="r.driver.avatarUrl" [name]="r.driver.displayName" [size]="40" />
                    <span class="min-w-0 flex-1"><span class="block truncate font-semibold">{{ r.fromName }} → {{ r.toName }}</span>
                      <span class="block text-sm text-slate-500 dark:text-slate-400">{{ r.departureAt | date: 'EEE d MMM, h:mm a' }} · {{ r.driver.displayName }}</span></span>
                    @if (r.ladiesOnly) { <span class="badge badge-muted">Women only</span> }
                    <app-icon name="arrowRight" [size]="18" class="text-slate-400" />
                  </a>
                </li>
              }
            </ul>
          } @else { <p class="card p-5 text-center text-sm text-slate-600 dark:text-slate-400">No rides from squad members yet. <a routerLink="/publish" class="font-semibold text-brand-700 underline dark:text-brand-400">Offer one</a></p> }
        </section>

        <section class="mt-5" aria-labelledby="members-heading">
          <h2 id="members-heading" class="kicker mb-2">{{ d.members.length }} {{ d.members.length === 1 ? 'member' : 'members' }}</h2>
          <ul class="card divide-y divide-slate-100 dark:divide-slate-800">
            @for (m of d.members; track m.id) {
              <li><a [routerLink]="['/u', m.id]" class="flex items-center gap-3 p-3 hover:bg-slate-50 dark:hover:bg-slate-800/50">
                <app-avatar [url]="m.avatarUrl" [name]="m.displayName" [size]="36" /><span class="font-semibold">{{ m.displayName }}</span>
                @if (m.idVerified) { <app-icon name="shield" [size]="15" class="text-brand-600" /> }</a></li>
            }
          </ul>
        </section>
        <div class="mt-6 text-center">
          @if (confirmLeave()) {
            <span class="text-sm">Leave {{ d.squad.name }}?</span>
            <button type="button" class="btn btn-ghost btn-sm" (click)="confirmLeave.set(false)">Stay</button>
            <button type="button" class="btn btn-sm bg-rose-600 text-white hover:bg-rose-700" (click)="leave()" [disabled]="busy()">Leave</button>
          } @else { <button type="button" class="btn btn-ghost btn-sm text-rose-600 dark:text-rose-400" (click)="confirmLeave.set(true)">Leave squad</button> }
        </div>
      } @else {
        <div class="skeleton mt-4 h-40"></div>
      }
    </div>
  `,
})
export class SquadPage {
  private readonly api = inject(SocialApi);
  private readonly router = inject(Router);
  private readonly toast = inject(ToastService);

  readonly id = input.required<string>();
  protected readonly data = signal<{ squad: SquadCard; members: MemberCard[]; rides: RideCard[] } | null>(null);
  protected readonly error = signal<string | null>(null);
  protected readonly busy = signal(false);
  protected readonly confirmLeave = signal(false);

  constructor() {
    effect(() => {
      const id = this.id();
      untracked(async () => {
        try {
          this.data.set(await this.api.squad(id));
        } catch (e) {
          this.error.set(problemOf(e).detail);
        }
      });
    });
  }

  protected async copy(s: SquadCard): Promise<void> {
    try {
      await navigator.clipboard.writeText(s.code);
      this.toast.success('Code copied. Share it with your squad.');
    } catch {
      this.toast.info(s.code);
    }
  }

  protected async leave(): Promise<void> {
    this.busy.set(true);
    try {
      await this.api.leaveSquad(this.id());
      this.toast.success('You left the squad.');
      void this.router.navigateByUrl('/community');
    } catch (e) {
      this.toast.error(problemOf(e).detail);
    } finally {
      this.busy.set(false);
    }
  }
}
