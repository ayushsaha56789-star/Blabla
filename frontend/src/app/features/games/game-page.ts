import { ChangeDetectionStrategy, Component, computed, DestroyRef, effect, inject, input, signal, untracked } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { problemOf } from '../../core/errors';
import { clockText, EMOJIS, GamesApi, GameView, LUDO_COLOURS, PHRASES, phraseText, SeatView } from '../../core/games-api';
import { ToastService } from '../../core/toast.service';
import { Avatar } from '../../shared/avatar';
import { Icon } from '../../shared/icon';
import { ChessBoard } from './chess-board';
import { LudoBoard } from './ludo-board';

const DOT = ['bg-orange-500', 'bg-emerald-500', 'bg-sky-500', 'bg-violet-500'];
const DIE = ['', '⚀', '⚁', '⚂', '⚃', '⚄', '⚅'];

/** One game: the waiting room, then the board, the players, the chat and the result. */
@Component({
  selector: 'app-game-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, Avatar, Icon, ChessBoard, LudoBoard],
  template: `
    <div class="container-page py-6 sm:py-8">
      <a routerLink="/games" class="btn btn-ghost btn-sm -ml-2"><app-icon name="arrowLeft" [size]="16" /> Games</a>
      @if (error()) {
        <p class="card mt-4 p-6 text-center" role="alert">{{ error() }}</p>
      } @else if (view(); as v) {
        @if (v.status === 'WAITING') {
          <section class="card mx-auto mt-4 max-w-lg p-6 text-center" aria-labelledby="lobby-heading">
            <p class="kicker">{{ v.game === 'CHESS' ? 'Chess' : 'Ludo' }} · {{ modeName(v) }}</p>
            <h1 id="lobby-heading" class="mt-1 text-2xl font-extrabold">Waiting for players</h1>
            @if (v.code) {
              <p class="mt-4 text-sm text-slate-600 dark:text-slate-400">Share this code with {{ v.mode === 'RIDE' ? 'the people on your ride' : 'your friends' }}:</p>
              <p class="mt-2 font-mono text-4xl font-extrabold tracking-[0.3em] text-brand-700 dark:text-brand-300">{{ v.code }}</p>
              <button type="button" class="btn btn-secondary btn-sm mt-3" (click)="copyLink(v)"><app-icon name="copy" [size]="16" /> Copy invite link</button>
            } @else {
              <p class="mt-4 text-sm text-slate-600 dark:text-slate-400">Looking for someone to play with… This page updates by itself.</p>
            }
            <ul class="mt-5 space-y-2 text-left">
              @for (s of v.seats; track s.seat) {
                <li class="flex items-center gap-3 rounded-2xl bg-slate-50 p-3 dark:bg-slate-800/60">
                  <app-avatar [url]="s.member?.avatarUrl ?? null" [name]="s.name" [size]="36" />
                  <span class="flex-1 font-semibold">{{ s.name }}@if (s.you) { <span class="font-normal text-slate-500"> (you)</span> }</span>
                </li>
              }
              @for (e of emptySeats(); track $index) {
                <li class="flex items-center gap-3 rounded-2xl border-2 border-dashed border-slate-200 p-3 text-sm text-slate-500 dark:border-slate-700">Empty seat</li>
              }
            </ul>
            @if (v.host) {
              @if (v.game === 'LUDO') {
                <label class="mt-4 flex items-center justify-center gap-2 text-sm"><input type="checkbox" class="h-4 w-4 accent-brand-600" [checked]="fill()" (change)="fill.set($any($event.target).checked)" /> Fill empty seats with computer players</label>
              }
              <button type="button" class="btn btn-primary mt-4 w-full" (click)="start()" [disabled]="busy()">Start the game</button>
            } @else {
              <p class="mt-4 text-sm text-slate-500 dark:text-slate-400">The game starts when the room is full, or when the host starts it.</p>
            }
            <button type="button" class="btn btn-ghost btn-sm mt-2" (click)="leave()" [disabled]="busy()">Leave the room</button>
          </section>
        } @else {
          <div class="mt-3 grid gap-6 lg:grid-cols-[minmax(0,34rem)_1fr] lg:items-start">
            <div class="min-w-0">
              <p class="mb-3 text-center text-base font-semibold" role="status" aria-live="polite">{{ headline() }}</p>
              @if (v.chess; as c) {
                <app-chess-board [fen]="c.fen" [legal]="c.legal" [flipped]="v.mySeat === 1" [lastMove]="c.lastMove" [check]="c.check"
                                 [whiteToMove]="c.whiteToMove" (move)="move($event)" />
              }
              @if (v.ludo; as l) {
                <app-ludo-board [view]="l" [mySeat]="v.status === 'PLAYING' ? v.mySeat : null" (token)="token($event)" />
                @if (v.status === 'PLAYING') {
                  <div class="mt-4 flex items-center justify-center gap-4">
                    <span class="text-6xl leading-none" [class]="l.dice ? 'text-slate-900 dark:text-white' : 'text-slate-300 dark:text-slate-600'" aria-hidden="true">{{ die(l.dice) }}</span>
                    @if (l.canRoll) {
                      <button type="button" class="btn btn-primary px-8 py-4 text-lg" (click)="roll()" [disabled]="busy()">Roll</button>
                    } @else if (l.movable.length) {
                      <p class="text-sm font-semibold">Tap a glowing token</p>
                    }
                  </div>
                }
                @if (l.last) { <p class="mt-2 text-center text-sm text-slate-600 dark:text-slate-400">{{ l.last }}</p> }
              }
            </div>

            <aside class="space-y-4">
              @if (v.status === 'FINISHED' || v.status === 'ABANDONED') {
                <section class="card p-5 text-center">
                  <p class="text-3xl">{{ outcomeEmoji() }}</p>
                  <p class="mt-1 text-xl font-extrabold">{{ outcomeText() }}</p>
                  <p class="text-sm text-slate-600 dark:text-slate-400">{{ v.result }}</p>
                  @if (me()?.ratingChange !== null && me()?.ratingChange !== undefined) {
                    <p class="mt-1 text-sm font-semibold" [class]="me()!.ratingChange! >= 0 ? 'text-brand-700 dark:text-brand-400' : 'text-rose-600 dark:text-rose-400'">Rating {{ me()!.ratingChange! >= 0 ? '+' : '' }}{{ me()!.ratingChange }}</p>
                  }
                  <div class="mt-4 grid grid-cols-2 gap-2">
                    <a routerLink="/games" class="btn btn-secondary">All games</a>
                    @if (v.mode === 'COMPUTER') { <button type="button" class="btn btn-primary" (click)="again(v)" [disabled]="busy()">Play again</button> }
                    @else { <a routerLink="/games" class="btn btn-primary">New game</a> }
                  </div>
                </section>
              }

              <section class="card p-4" aria-label="Players">
                <ul class="space-y-2">
                  @for (s of v.seats; track s.seat) {
                    <li class="flex items-center gap-3 rounded-2xl p-2" [class]="v.turnSeat === s.seat ? 'bg-brand-50 ring-2 ring-brand-500 dark:bg-brand-500/15' : ''">
                      <span class="relative">
                        @if (s.computer) { <span class="flex h-10 w-10 items-center justify-center rounded-full bg-slate-100 dark:bg-slate-800"><app-icon name="monitor" [size]="20" /></span> }
                        @else { <app-avatar [url]="s.member?.avatarUrl ?? null" [name]="s.name" [size]="40" /> }
                        <span class="absolute -right-1 -bottom-1 h-4 w-4 rounded-full border-2 border-white dark:border-slate-900" [class]="seatColour(v, s)"></span>
                      </span>
                      <span class="min-w-0 flex-1">
                        <span class="block truncate font-semibold">{{ s.name }}@if (s.you) { <span class="font-normal text-slate-500"> (you)</span> }</span>
                        <span class="block text-xs text-slate-500 dark:text-slate-400">{{ seatNote(v, s) }}</span>
                      </span>
                      @if (v.chess && v.chess.whiteMs !== null) {
                        <span class="rounded-lg px-2 py-1 font-mono text-sm font-bold" [class]="v.turnSeat === s.seat ? 'bg-slate-900 text-white dark:bg-white dark:text-slate-900' : 'bg-slate-100 dark:bg-slate-800'">{{ clock(s.seat) }}</span>
                      } @else if (v.turnSeat === s.seat && secondsLeft() !== null && !s.computer) {
                        <span class="text-xs font-semibold text-slate-500">{{ secondsLeft() }}s</span>
                      }
                      @if (s.outcome) { <span class="badge" [class]="s.outcome === 'WIN' ? 'badge-success' : s.outcome === 'DRAW' ? 'badge-muted' : 'badge-danger'">{{ s.outcome === 'WIN' ? 'Won' : s.outcome === 'DRAW' ? 'Draw' : 'Lost' }}</span> }
                    </li>
                  }
                </ul>
              </section>

              @if (v.chess; as c) {
                @if (c.moves.length) {
                  <section class="card p-4" aria-label="Moves">
                    <p class="kicker mb-2">Moves</p>
                    <ol class="grid max-h-40 grid-cols-[2rem_5rem_5rem] gap-x-3 gap-y-0.5 overflow-y-auto font-mono text-sm">
                      @for (pair of movePairs(); track $index) {
                        <li class="contents"><span class="text-slate-400">{{ $index + 1 }}.</span><span>{{ pair[0] }}</span><span>{{ pair[1] }}</span></li>
                      }
                    </ol>
                  </section>
                }
              }

              <section class="card p-4" aria-label="Chat">
                @if (v.messages.length) {
                  <ul class="mb-3 space-y-1 text-sm">
                    @for (m of v.messages.slice(-4); track $index) { <li><span class="font-semibold">{{ m.name }}:</span> {{ say(m.phrase) }}</li> }
                  </ul>
                }
                <div class="flex flex-wrap gap-1.5">
                  @for (p of phrases; track p) { <button type="button" class="chip text-xs" (click)="chat(p)" [disabled]="busy()">{{ say(p) }}</button> }
                  @for (e of emojis; track e) { <button type="button" class="chip px-2 text-base" (click)="chat(e)" [disabled]="busy()" [attr.aria-label]="'Send ' + e">{{ e }}</button> }
                </div>
              </section>

              @if (v.status === 'PLAYING') {
                @if (v.game === 'CHESS') {
                  @if (confirmResign()) {
                    <div class="card flex items-center gap-2 p-3 text-sm"><span class="flex-1">Resign this game?</span>
                      <button type="button" class="btn btn-ghost btn-sm" (click)="confirmResign.set(false)">No</button>
                      <button type="button" class="btn btn-sm bg-rose-600 text-white hover:bg-rose-700" (click)="resign()" [disabled]="busy()">Resign</button></div>
                  } @else { <button type="button" class="btn btn-ghost btn-sm w-full text-rose-600 dark:text-rose-400" (click)="confirmResign.set(true)"><app-icon name="flag" [size]="16" /> Resign</button> }
                } @else {
                  <button type="button" class="btn btn-ghost btn-sm w-full" (click)="leave()" [disabled]="busy()">Leave (the computer plays for you)</button>
                }
              }
            </aside>
          </div>
        }
      } @else {
        <div class="skeleton mx-auto mt-4 aspect-square max-w-xl"></div>
      }
    </div>
  `,
})
export class GamePage {
  private readonly api = inject(GamesApi);
  private readonly router = inject(Router);
  private readonly toast = inject(ToastService);

  readonly id = input.required<string>();

  protected readonly view = signal<GameView | null>(null);
  protected readonly error = signal<string | null>(null);
  protected readonly busy = signal(false);
  protected readonly fill = signal(true);
  protected readonly confirmResign = signal(false);
  protected readonly phrases = Object.keys(PHRASES);
  protected readonly emojis = EMOJIS;
  private readonly now = signal(Date.now());
  private readonly receivedAt = signal(Date.now());
  private serverOffset = 0;
  private polling = false;

  protected readonly me = computed(() => this.view()?.seats.find((s) => s.you) ?? null);
  protected readonly emptySeats = computed(() => {
    const v = this.view();
    return v ? Array.from({ length: Math.max(0, v.maxPlayers - v.seats.length) }) : [];
  });
  protected readonly movePairs = computed(() => {
    const moves = this.view()?.chess?.moves ?? [];
    const pairs: string[][] = [];
    for (let i = 0; i < moves.length; i += 2) pairs.push([moves[i], moves[i + 1] ?? '']);
    return pairs;
  });
  protected readonly secondsLeft = computed(() => {
    const v = this.view();
    if (!v?.deadline || v.status !== 'PLAYING') return null;
    return Math.max(0, Math.ceil((Date.parse(v.deadline) - (this.now() + this.serverOffset)) / 1000));
  });
  protected readonly headline = computed(() => {
    const v = this.view();
    if (!v) return '';
    if (v.status === 'FINISHED' || v.status === 'ABANDONED') return v.result ?? 'Game over';
    const turn = v.seats.find((s) => s.seat === v.turnSeat);
    if (!turn) return '';
    if (turn.you) {
      if (v.chess) return v.chess.check ? 'Check! Your move' : 'Your move';
      return v.ludo?.canRoll ? 'Your turn: roll the dice' : 'Your turn: move a token';
    }
    return `${turn.name} is ${turn.computer ? 'thinking' : 'playing'}…`;
  });
  protected readonly outcomeText = computed(() => {
    const o = this.me()?.outcome;
    return o === 'WIN' ? 'You won!' : o === 'DRAW' ? 'A draw' : o === 'LOSS' ? 'You lost this one' : 'Game over';
  });
  protected readonly outcomeEmoji = computed(() => {
    const o = this.me()?.outcome;
    return o === 'WIN' ? '🏆' : o === 'DRAW' ? '🤝' : o === 'LOSS' ? '🙂' : '🎲';
  });

  constructor() {
    effect(() => {
      const id = this.id();
      untracked(() => {
        this.view.set(null);
        void this.load(id);
      });
    });
    const timer = setInterval(() => {
      this.now.set(Date.now());
      void this.poll();
    }, 1000);
    inject(DestroyRef).onDestroy(() => clearInterval(timer));
  }

  protected modeName(v: GameView): string {
    return v.mode === 'RIDE' ? 'With co-travellers' : v.mode === 'QUICK' ? 'Quick match' : v.mode === 'COMPUTER' ? 'Against the computer' : 'With friends';
  }

  protected say(p: string): string {
    return phraseText(p);
  }

  protected die(n: number | null): string {
    return DIE[n ?? 6];
  }

  protected seatColour(v: GameView, s: SeatView): string {
    if (v.game === 'LUDO') return DOT[s.seat] ?? 'bg-slate-400';
    return s.seat === 0 ? 'bg-white ring-1 ring-slate-300' : 'bg-slate-900';
  }

  protected seatNote(v: GameView, s: SeatView): string {
    const side = v.game === 'LUDO' ? LUDO_COLOURS[s.seat] : s.seat === 0 ? 'White' : 'Black';
    return side + (s.away ? ' · away, the computer is playing' : '');
  }

  /** A chess clock, running for the side to move. */
  protected clock(seat: number): string {
    const c = this.view()?.chess;
    if (!c || c.whiteMs === null || c.blackMs === null) return '';
    const base = seat === 0 ? c.whiteMs : c.blackMs;
    const running = this.view()?.status === 'PLAYING' && this.view()?.turnSeat === seat;
    return clockText(running ? base - (this.now() - this.receivedAt()) : base);
  }

  protected move(uci: string): Promise<void> {
    return this.act(() => this.api.move(this.id(), uci));
  }

  protected roll(): Promise<void> {
    return this.act(() => this.api.roll(this.id()));
  }

  protected token(t: number): Promise<void> {
    return this.act(() => this.api.token(this.id(), t));
  }

  protected start(): Promise<void> {
    return this.act(() => this.api.start(this.id(), this.fill()));
  }

  protected chat(phrase: string): Promise<void> {
    return this.act(() => this.api.say(this.id(), phrase));
  }

  protected async resign(): Promise<void> {
    await this.act(() => this.api.resign(this.id()));
    this.confirmResign.set(false);
  }

  protected async leave(): Promise<void> {
    this.busy.set(true);
    try {
      await this.api.leave(this.id());
      void this.router.navigateByUrl('/games');
    } catch (e) {
      this.toast.error(problemOf(e).detail);
    } finally {
      this.busy.set(false);
    }
  }

  protected async again(v: GameView): Promise<void> {
    this.busy.set(true);
    try {
      const mine = v.seats.find((s) => s.you);
      const next = await this.api.create({
        game: v.game, mode: 'COMPUTER', level: v.level ?? 'MEDIUM', players: v.seats.length,
        colour: v.game === 'CHESS' ? (mine?.seat === 1 ? 'WHITE' : 'BLACK') : undefined,
      });
      void this.router.navigate(['/games', next.id]);
    } catch (e) {
      this.toast.error(problemOf(e).detail);
    } finally {
      this.busy.set(false);
    }
  }

  protected async copyLink(v: GameView): Promise<void> {
    const link = `${location.origin}/games?code=${v.code}`;
    try {
      await navigator.clipboard.writeText(link);
      this.toast.success('Invite link copied.');
    } catch {
      this.toast.info(link);
    }
  }

  private set(v: GameView): void {
    this.view.set(v);
    this.receivedAt.set(Date.now());
    this.serverOffset = Date.parse(v.serverNow) - Date.now();
  }

  private async act(call: () => Promise<GameView>): Promise<void> {
    this.busy.set(true);
    try {
      this.set(await call());
    } catch (e) {
      const p = problemOf(e);
      this.toast.error(p.errors ? Object.values(p.errors)[0] : p.detail);
      await this.load(this.id());
    } finally {
      this.busy.set(false);
    }
  }

  private async load(id: string): Promise<void> {
    try {
      const v = await this.api.room(id, null);
      if (v) this.set(v);
    } catch (e) {
      this.error.set(problemOf(e).detail);
    }
  }

  /** Asks the server for news (it also plays any turns that are due). Quiet when the tab is hidden or the game is over. */
  private async poll(): Promise<void> {
    const v = this.view();
    if (!v || this.polling || this.busy() || document.hidden || v.status === 'FINISHED' || v.status === 'ABANDONED') return;
    this.polling = true;
    try {
      const next = await this.api.room(this.id(), v.version);
      if (next) this.set(next);
    } catch {
      // A missed update comes with the next one.
    } finally {
      this.polling = false;
    }
  }
}
