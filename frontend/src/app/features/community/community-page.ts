import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, inject, OnInit, signal } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { problemOf } from '../../core/errors';
import { FeaturesService } from '../../core/features.service';
import { EventCard, SocialApi, SquadCard, SquadKind } from '../../core/social-api';
import { ToastService } from '../../core/toast.service';
import { Icon } from '../../shared/icon';

const KIND_NAMES: Record<SquadKind, string> = { COLLEGE: 'College', OFFICE: 'Office', FRIENDS: 'Friends' };

/** Squads (your college, office or friends) and events with the rides going there. */
@Component({
  selector: 'app-community-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [DatePipe, RouterLink, Icon],
  template: `
    <div class="container-page max-w-4xl py-8 sm:py-10">
      <p class="kicker">Community</p>
      <h1 class="mt-1 text-3xl font-extrabold tracking-tight">Squads &amp; events</h1>
      <p class="mt-2 text-slate-600 dark:text-slate-400">Ride with your college, your office, your people, and find rides to the events everyone's going to.</p>
      @if (features.isOn('community.squads') && features.isOn('community.events')) {
        <div class="segmented mt-5 grid-cols-2 sm:max-w-xs" role="tablist" aria-label="Show">
          <button type="button" role="tab" class="segment" [class.segment-on]="tab() === 'squads'" [attr.aria-selected]="tab() === 'squads'" (click)="tab.set('squads')">Squads</button>
          <button type="button" role="tab" class="segment" [class.segment-on]="tab() === 'events'" [attr.aria-selected]="tab() === 'events'" (click)="tab.set('events')">Events</button>
        </div>
      }

      @if (tab() === 'squads' && features.isOn('community.squads')) {
        <section class="mt-6" aria-label="Your squads">
          @if (squads(); as list) {
            @if (list.length) {
              <ul class="grid gap-3 sm:grid-cols-2">
                @for (s of list; track s.id) {
                  <li>
                    <a [routerLink]="['/community/squads', s.id]" class="card flex items-center gap-3 p-4 transition hover:shadow-md">
                      <span class="flex h-12 w-12 shrink-0 items-center justify-center rounded-2xl bg-rose-50 text-2xl dark:bg-rose-500/15" aria-hidden="true">{{ s.kind === 'COLLEGE' ? '🎓' : s.kind === 'OFFICE' ? '💼' : '🫶' }}</span>
                      <span class="min-w-0 flex-1"><span class="block truncate font-bold">{{ s.name }}</span>
                        <span class="block text-sm text-slate-500 dark:text-slate-400">{{ kinds[s.kind] }} · {{ s.members }} {{ s.members === 1 ? 'member' : 'members' }}@if (s.emailDomain) { · &#64;{{ s.emailDomain }} }</span></span>
                      <app-icon name="arrowRight" [size]="18" class="text-slate-400" />
                    </a>
                  </li>
                }
              </ul>
            } @else { <p class="card p-6 text-center text-sm text-slate-600 dark:text-slate-400">You're not in a squad yet. Start one, or join with a code from a friend.</p> }
          } @else { <div class="skeleton h-20"></div> }

          <div class="mt-6 grid gap-4 sm:grid-cols-2">
            <form class="card p-5" (submit)="create($event)" aria-labelledby="new-squad-heading">
              <h2 id="new-squad-heading" class="font-bold">Start a squad</h2>
              <label class="label mt-3 text-xs" for="sq-name">Name</label>
              <input id="sq-name" class="input" maxlength="60" placeholder="e.g. IIT Delhi riders" [value]="name()" (input)="name.set($any($event.target).value)" />
              <div class="segmented mt-3 grid-cols-3 text-sm" role="radiogroup" aria-label="Kind">
                @for (k of kindList; track k) {
                  <button type="button" role="radio" class="segment" [class.segment-on]="kind() === k" [attr.aria-checked]="kind() === k" (click)="kind.set(k)">{{ kinds[k] }}</button>
                }
              </div>
              @if (kind() !== 'FRIENDS') {
                <label class="label mt-3 text-xs" for="sq-domain">Only people with an email at <span class="font-normal">(optional)</span></label>
                <input id="sq-domain" class="input" maxlength="100" placeholder="e.g. iitd.ac.in" [value]="domain()" (input)="domain.set($any($event.target).value)" />
                <p class="hint">Members need a confirmed email at this domain (Profile → Verification).</p>
              }
              <button type="submit" class="btn btn-primary mt-4 w-full" [disabled]="busy() || name().trim().length < 3">Create squad</button>
            </form>
            <form class="card p-5" (submit)="join($event)" aria-labelledby="join-squad-heading">
              <h2 id="join-squad-heading" class="font-bold">Join with a code</h2>
              <p class="mt-1 text-sm text-slate-600 dark:text-slate-400">Ask a squad member for its 6-character code.</p>
              <label class="sr-only" for="sq-code">Squad code</label>
              <input id="sq-code" class="input mt-3 font-mono uppercase tracking-widest" maxlength="6" placeholder="Code" [value]="code()" (input)="code.set($any($event.target).value)" />
              <button type="submit" class="btn btn-secondary mt-4 w-full" [disabled]="busy() || code().trim().length < 6">Join squad</button>
            </form>
          </div>
        </section>
      }

      @if (tab() === 'events' && features.isOn('community.events')) {
        <section class="mt-6" aria-label="Events">
          @if (events(); as list) {
            @if (list.length) {
              <ul class="grid gap-3 sm:grid-cols-2">
                @for (e of list; track e.id) {
                  <li>
                    <a [routerLink]="['/community/events', e.id]" class="card block overflow-hidden transition hover:shadow-md">
                      <div class="bg-linear-to-br from-rose-500 to-fuchsia-600 p-4 text-white">
                        <p class="text-xs font-semibold tracking-wider uppercase opacity-90">{{ e.startsAt | date: 'EEE d MMM · h:mm a' }}</p>
                        <p class="mt-1 text-lg font-extrabold">{{ e.title }}</p>
                      </div>
                      <p class="p-4 text-sm text-slate-600 dark:text-slate-400"><app-icon name="pin" [size]="14" /> {{ e.venue }}, {{ e.placeName }}</p>
                    </a>
                  </li>
                }
              </ul>
            } @else { <p class="card p-6 text-center text-sm text-slate-600 dark:text-slate-400">No events listed right now. Check back soon!</p> }
          } @else { <div class="skeleton h-20"></div> }
        </section>
      }
    </div>
  `,
})
export class CommunityPage implements OnInit {
  private readonly api = inject(SocialApi);
  private readonly router = inject(Router);
  private readonly toast = inject(ToastService);
  protected readonly features = inject(FeaturesService);

  protected readonly kinds = KIND_NAMES;
  protected readonly kindList: SquadKind[] = ['COLLEGE', 'OFFICE', 'FRIENDS'];
  protected readonly tab = signal<'squads' | 'events'>('squads');
  protected readonly squads = signal<SquadCard[] | null>(null);
  protected readonly events = signal<EventCard[] | null>(null);
  protected readonly busy = signal(false);
  protected readonly name = signal('');
  protected readonly kind = signal<SquadKind>('COLLEGE');
  protected readonly domain = signal('');
  protected readonly code = signal('');

  async ngOnInit(): Promise<void> {
    if (!this.features.isOn('community.squads')) this.tab.set('events');
    if (this.features.isOn('community.squads')) this.squads.set(await this.api.squads().catch(() => []));
    if (this.features.isOn('community.events')) this.events.set(await this.api.events().catch(() => []));
  }

  protected create(event: Event): Promise<void> {
    event.preventDefault();
    return this.go(() => this.api.createSquad(this.name().trim(), this.kind(), this.kind() === 'FRIENDS' ? null : this.domain().trim() || null));
  }

  protected join(event: Event): Promise<void> {
    event.preventDefault();
    return this.go(() => this.api.joinSquad(this.code().trim()));
  }

  private async go(call: () => Promise<{ squad: SquadCard }>): Promise<void> {
    this.busy.set(true);
    try {
      const s = await call();
      void this.router.navigate(['/community/squads', s.squad.id]);
    } catch (e) {
      const p = problemOf(e);
      this.toast.error(p.errors ? Object.values(p.errors)[0] : p.detail);
    } finally {
      this.busy.set(false);
    }
  }
}
