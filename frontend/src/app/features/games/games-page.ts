import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, inject, input, OnInit, signal } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { problemOf } from '../../core/errors';
import { FeaturesService } from '../../core/features.service';
import { Game, gamePlayers, GamesApi, GameSummary, LeaderRow, Level } from '../../core/games-api';
import { ToastService } from '../../core/toast.service';
import { Avatar } from '../../shared/avatar';
import { Icon } from '../../shared/icon';

/** Games: chess and ludo against the computer, with friends or a quick match; your games; the leaderboard. */
@Component({
  selector: 'app-games-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [DatePipe, RouterLink, Avatar, Icon],
  template: `
    <section class="relative overflow-hidden bg-linear-to-br from-violet-50 via-white to-fuchsia-50 dark:from-slate-950 dark:via-slate-950 dark:to-fuchsia-950/30">
      <div class="container-page py-10 sm:py-12">
        <p class="kicker">Games</p>
        <h1 class="mt-1 max-w-2xl text-3xl font-extrabold tracking-tight sm:text-5xl">Chess and ludo for the road.</h1>
        <p class="mt-3 max-w-xl text-slate-600 dark:text-slate-400">Free, fair and friendly: no money, no prizes, no ads in the middle of your game. Every move is checked by our server.</p>
        @if (features.isOn('games.online')) {
          <form class="mt-5 flex max-w-md gap-2" (submit)="join($event)">
            <label class="sr-only" for="game-code">Room code</label>
            <input id="game-code" class="input flex-1 font-mono uppercase tracking-widest" maxlength="6" autocomplete="off" placeholder="Room code" [value]="code()" (input)="code.set($any($event.target).value)" />
            <button type="submit" class="btn btn-secondary" [disabled]="busy() || code().trim().length < 6">Join</button>
          </form>
        }
      </div>
    </section>

    <div class="container-page py-8">
      @if (features.isOn('games.vs_computer')) {
        <div class="mb-4 flex flex-wrap items-center gap-3">
          <span class="label m-0" id="level-label">Computer level</span>
          <div class="segmented grid-cols-3 sm:w-80" role="radiogroup" aria-labelledby="level-label">
            @for (l of levels; track l.value) {
              <button type="button" role="radio" class="segment" [class.segment-on]="level() === l.value" [attr.aria-checked]="level() === l.value" (click)="level.set(l.value)">{{ l.label }}</button>
            }
          </div>
        </div>
      }
      <div class="grid gap-5 md:grid-cols-2">
        @if (features.isOn('games.chess')) {
          <section class="card overflow-hidden" aria-labelledby="chess-heading">
            <div class="flex items-center gap-4 bg-linear-to-br from-emerald-600 to-emerald-800 p-5 text-white">
              <span class="text-5xl leading-none" aria-hidden="true">&#9822;&#xFE0E;</span>
              <div><h2 id="chess-heading" class="text-xl font-extrabold">Chess</h2><p class="text-sm text-emerald-50/90">Ten-minute clocks online. Tap a piece to see its moves.</p></div>
            </div>
            <div class="space-y-3 p-5">
              @if (features.isOn('games.vs_computer')) {
                <div class="flex flex-wrap items-center gap-2">
                  <div class="segmented grid-cols-3 text-sm" role="radiogroup" aria-label="Play as">
                    @for (c of colours; track c.value) {
                      <button type="button" role="radio" class="segment px-3" [class.segment-on]="colour() === c.value" [attr.aria-checked]="colour() === c.value" (click)="colour.set(c.value)">{{ c.label }}</button>
                    }
                  </div>
                  <button type="button" class="btn btn-primary flex-1" (click)="computer('CHESS')" [disabled]="busy()"><app-icon name="monitor" [size]="18" /> Play the computer</button>
                </div>
              }
              @if (features.isOn('games.online')) {
                <div class="grid grid-cols-2 gap-2">
                  <button type="button" class="btn btn-secondary" (click)="friends('CHESS')" [disabled]="busy()"><app-icon name="users" [size]="18" /> With a friend</button>
                  <button type="button" class="btn btn-secondary" (click)="quick('CHESS')" [disabled]="busy()"><app-icon name="sparkles" [size]="18" /> Quick match</button>
                </div>
              }
            </div>
          </section>
        }
        @if (features.isOn('games.ludo')) {
          <section class="card overflow-hidden" aria-labelledby="ludo-heading">
            <div class="flex items-center gap-4 bg-linear-to-br from-orange-500 via-fuchsia-500 to-sky-500 p-5 text-white">
              <span class="text-5xl leading-none" aria-hidden="true">&#9860;</span>
              <div><h2 id="ludo-heading" class="text-xl font-extrabold">Ludo</h2><p class="text-sm text-white/90">2 to 4 players. One tap to roll; single moves play themselves.</p></div>
            </div>
            <div class="space-y-3 p-5">
              <div class="segmented grid-cols-3 text-sm" role="radiogroup" aria-label="Players">
                @for (n of [2, 3, 4]; track n) {
                  <button type="button" role="radio" class="segment" [class.segment-on]="players() === n" [attr.aria-checked]="players() === n" (click)="players.set(n)">{{ n }} players</button>
                }
              </div>
              @if (features.isOn('games.vs_computer')) {
                <button type="button" class="btn btn-primary w-full" (click)="computer('LUDO')" [disabled]="busy()"><app-icon name="monitor" [size]="18" /> Play the computer</button>
              }
              @if (features.isOn('games.online')) {
                <div class="grid grid-cols-2 gap-2">
                  <button type="button" class="btn btn-secondary" (click)="friends('LUDO')" [disabled]="busy()"><app-icon name="users" [size]="18" /> With friends</button>
                  <button type="button" class="btn btn-secondary" (click)="quick('LUDO')" [disabled]="busy()"><app-icon name="sparkles" [size]="18" /> Quick match</button>
                </div>
              }
            </div>
          </section>
        }
      </div>

      <div class="mt-8 grid gap-6 lg:grid-cols-2">
        <section aria-labelledby="mine-heading">
          <h2 id="mine-heading" class="kicker mb-3">Your games</h2>
          @if (mine(); as list) {
            @if (!list.length) { <p class="card p-6 text-center text-sm text-slate-600 dark:text-slate-400">No games yet. Start one above!</p> }
            <ul class="space-y-2">
              @for (g of list; track g.id) {
                <li>
                  <a [routerLink]="['/games', g.id]" class="card flex items-center gap-3 p-3 transition hover:shadow-md">
                    <span class="flex h-10 w-10 shrink-0 items-center justify-center rounded-xl bg-slate-100 text-2xl dark:bg-slate-800" aria-hidden="true">{{ g.game === 'CHESS' ? '♞︎' : '⚄' }}</span>
                    <span class="min-w-0 flex-1">
                      <span class="block truncate font-semibold">{{ g.game === 'CHESS' ? 'Chess' : 'Ludo' }} with {{ who(g.players) }}</span>
                      <span class="block text-xs text-slate-500 dark:text-slate-400">{{ g.result ?? statusText(g) }} · {{ g.updatedAt | date: 'd MMM, h:mm a' }}</span>
                    </span>
                    @if (g.myTurn) { <span class="badge badge-warning">Your turn</span> }
                    @else if (g.outcome) { <span class="badge" [class]="g.outcome === 'WIN' ? 'badge-success' : 'badge-muted'">{{ g.outcome === 'WIN' ? 'Won' : g.outcome === 'DRAW' ? 'Draw' : 'Lost' }}</span> }
                  </a>
                </li>
              }
            </ul>
          } @else { <div class="skeleton h-24"></div> }
        </section>

        @if (features.isOn('games.leaderboards')) {
          <section aria-labelledby="board-heading">
            <div class="mb-3 flex items-center justify-between gap-2">
              <h2 id="board-heading" class="kicker">Leaderboard</h2>
              <div class="segmented grid-cols-2 text-sm" role="radiogroup" aria-label="Game">
                @for (g of games; track g) {
                  <button type="button" role="radio" class="segment px-3" [class.segment-on]="boardGame() === g" [attr.aria-checked]="boardGame() === g" (click)="showBoard(g)">{{ g === 'CHESS' ? 'Chess' : 'Ludo' }}</button>
                }
              </div>
            </div>
            @if (board(); as b) {
              @if (!b.top.length) { <p class="card p-6 text-center text-sm text-slate-600 dark:text-slate-400">Win online games against other members to appear here.</p> }
              @else {
                <ol class="card divide-y divide-slate-100 dark:divide-slate-800">
                  @for (r of b.top; track r.rank) {
                    <li class="flex items-center gap-3 p-3" [class]="r.member.id === myId() ? 'bg-brand-50 dark:bg-brand-500/10' : ''">
                      <span class="w-6 text-center font-bold text-slate-500">{{ r.rank }}</span>
                      <app-avatar [url]="r.member.avatarUrl" [name]="r.member.displayName" [size]="32" />
                      <span class="min-w-0 flex-1 truncate font-semibold">{{ r.member.displayName }}</span>
                      <span class="text-xs text-slate-500 dark:text-slate-400">{{ r.won }}/{{ r.played }} won</span>
                      <span class="w-12 text-right font-mono font-bold">{{ r.rating }}</span>
                    </li>
                  }
                </ol>
              }
              @if (b.me; as me) { <p class="hint">You're #{{ me.rank }} with {{ me.rating }}.</p> }
            } @else { <div class="skeleton h-24"></div> }
          </section>
        }
      </div>
    </div>
  `,
})
export class GamesPage implements OnInit {
  private readonly api = inject(GamesApi);
  private readonly router = inject(Router);
  private readonly toast = inject(ToastService);
  protected readonly features = inject(FeaturesService);

  /** An invite link: /games?code=ABC234 joins at once. */
  readonly codeParam = input<string | undefined>(undefined, { alias: 'code' });

  protected readonly levels: { label: string; value: Level }[] = [
    { label: 'Easy', value: 'EASY' }, { label: 'Medium', value: 'MEDIUM' }, { label: 'Hard', value: 'HARD' },
  ];
  protected readonly colours: { label: string; value: 'WHITE' | 'BLACK' | 'RANDOM' }[] = [
    { label: 'White', value: 'WHITE' }, { label: 'Black', value: 'BLACK' }, { label: 'Either', value: 'RANDOM' },
  ];
  protected readonly games: Game[] = ['CHESS', 'LUDO'];
  protected readonly level = signal<Level>('EASY');
  protected readonly colour = signal<'WHITE' | 'BLACK' | 'RANDOM'>('WHITE');
  protected readonly players = signal(4);
  protected readonly code = signal('');
  protected readonly busy = signal(false);
  protected readonly mine = signal<GameSummary[] | null>(null);
  protected readonly boardGame = signal<Game>('CHESS');
  protected readonly board = signal<{ top: LeaderRow[]; me: LeaderRow | null } | null>(null);
  protected readonly myId = signal<string | null>(null);

  async ngOnInit(): Promise<void> {
    const code = this.codeParam();
    if (code) {
      this.code.set(code);
      await this.joinCode(code);
      return;
    }
    try {
      this.mine.set(await this.api.mine());
    } catch {
      this.mine.set([]);
    }
    if (this.features.isOn('games.leaderboards')) await this.showBoard('CHESS');
  }

  protected who(players: string[]): string {
    return gamePlayers(players);
  }

  protected statusText(g: GameSummary): string {
    return g.status === 'WAITING' ? 'Waiting for players' : g.status === 'PLAYING' ? 'In play' : 'Finished';
  }

  protected computer(game: Game): Promise<void> {
    return this.go(() => this.api.create({
      game, mode: 'COMPUTER', level: this.level(), players: game === 'LUDO' ? this.players() : undefined,
      colour: game === 'CHESS' ? this.colour() : undefined,
    }));
  }

  protected friends(game: Game): Promise<void> {
    return this.go(() => this.api.create({ game, mode: 'FRIENDS', players: game === 'LUDO' ? this.players() : undefined }));
  }

  protected quick(game: Game): Promise<void> {
    return this.go(() => this.api.quick(game));
  }

  protected join(event: Event): Promise<void> {
    event.preventDefault();
    return this.joinCode(this.code().trim());
  }

  protected async showBoard(game: Game): Promise<void> {
    this.boardGame.set(game);
    this.board.set(null);
    try {
      const b = await this.api.leaderboard(game);
      this.board.set(b);
      if (b.me) this.myId.set(b.me.member.id);
    } catch {
      this.board.set({ top: [], me: null });
    }
  }

  private joinCode(code: string): Promise<void> {
    return this.go(() => this.api.join(code));
  }

  private async go(call: () => Promise<{ id: string }>): Promise<void> {
    this.busy.set(true);
    try {
      const room = await call();
      void this.router.navigate(['/games', room.id]);
    } catch (e) {
      const p = problemOf(e);
      this.toast.error(p.errors ? Object.values(p.errors)[0] : p.detail);
      if (!this.mine()) this.mine.set(await this.api.mine().catch(() => []));
    } finally {
      this.busy.set(false);
    }
  }
}
