import { ChangeDetectionStrategy, Component, computed, effect, input, output, signal } from '@angular/core';
import { fenBoard, PIECE_GLYPH, squareIndex, squareName } from '../../core/games-api';

/**
 * A chess board: tap one of your pieces to see where it can go, then tap the square. The legal moves come from the
 * server; a pawn reaching the last rank asks which piece it becomes.
 */
@Component({
  selector: 'app-chess-board',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'block' },
  template: `
    <div class="relative grid aspect-square w-full grid-cols-8 overflow-hidden rounded-2xl border-4 border-emerald-900/80 shadow-xl select-none [container-type:inline-size] dark:border-emerald-950">
      @for (sq of order(); track sq) {
        <button type="button" class="relative flex items-center justify-center" [class]="squareClass(sq)" (click)="tap(sq)"
                [attr.aria-label]="label(sq)" [attr.aria-pressed]="selected() === sq">
          @if (board()[sq]; as p) {
            <span class="leading-none" [class]="p === p.toUpperCase() ? 'piece-white' : 'piece-black'" style="font-size: 10cqw">{{ glyph(p) }}</span>
          }
          @if (targets().has(sq)) {
            @if (board()[sq]) { <span class="pointer-events-none absolute inset-[6%] rounded-full border-[0.9cqw] border-black/30 dark:border-white/40"></span> }
            @else { <span class="pointer-events-none absolute h-[28%] w-[28%] rounded-full bg-black/25 dark:bg-white/35"></span> }
          }
          @if (fileLabel(sq); as f) { <span class="pointer-events-none absolute right-[6%] bottom-[2%] font-semibold opacity-70" style="font-size: 2.2cqw">{{ f }}</span> }
          @if (rankLabel(sq); as r) { <span class="pointer-events-none absolute top-[3%] left-[6%] font-semibold opacity-70" style="font-size: 2.2cqw">{{ r }}</span> }
        </button>
      }
      @if (promotion(); as pr) {
        <div class="absolute inset-0 flex items-center justify-center bg-black/50" role="dialog" aria-label="Choose a piece">
          <div class="flex gap-2 rounded-2xl bg-white p-3 shadow-2xl dark:bg-slate-800">
            @for (t of ['q', 'r', 'b', 'n']; track t) {
              <button type="button" class="flex h-[13cqw] w-[13cqw] items-center justify-center rounded-xl bg-emerald-50 hover:bg-emerald-100 dark:bg-slate-700"
                      (click)="promote(t)" [attr.aria-label]="promotionNames[t]">
                <span [class]="whiteToMove() ? 'piece-white' : 'piece-black'" style="font-size: 9cqw">{{ glyph(t) }}</span>
              </button>
            }
          </div>
        </div>
      }
    </div>
  `,
  styles: `
    .piece-white { color: #fff; text-shadow: 0 0 1px #111, 0 0 1px #111, 0 1px 2px rgb(0 0 0 / 0.55); }
    .piece-black { color: #18181b; text-shadow: 0 0 1px rgb(255 255 255 / 0.9), 0 0 2px rgb(255 255 255 / 0.45); }
  `,
})
export class ChessBoard {
  readonly fen = input.required<string>();
  readonly legal = input<string[]>([]);
  readonly flipped = input(false);
  readonly lastMove = input<string | null>(null);
  readonly check = input(false);
  readonly whiteToMove = input(true);
  readonly move = output<string>();

  protected readonly promotionNames: Record<string, string> = { q: 'Queen', r: 'Rook', b: 'Bishop', n: 'Knight' };
  protected readonly selected = signal<number | null>(null);
  protected readonly promotion = signal<{ from: number; to: number } | null>(null);

  protected readonly board = computed(() => fenBoard(this.fen()));
  protected readonly order = computed(() => {
    const squares: number[] = [];
    for (let row = 0; row < 8; row++) {
      for (let col = 0; col < 8; col++) {
        squares.push(this.flipped() ? row * 8 + (7 - col) : (7 - row) * 8 + col);
      }
    }
    return squares;
  });
  protected readonly targets = computed(() => {
    const from = this.selected();
    if (from === null) return new Set<number>();
    const name = squareName(from);
    return new Set(this.legal().filter((m) => m.startsWith(name)).map((m) => squareIndex(m.slice(2, 4))));
  });
  private readonly lastSquares = computed(() => {
    const m = this.lastMove();
    return m ? [squareIndex(m.slice(0, 2)), squareIndex(m.slice(2, 4))] : [];
  });
  private readonly kingInCheck = computed(() => {
    if (!this.check()) return -1;
    const king = this.whiteToMove() ? 'K' : 'k';
    return this.board().indexOf(king);
  });

  constructor() {
    // A new position clears the selection.
    effect(() => {
      this.fen();
      this.selected.set(null);
      this.promotion.set(null);
    });
  }

  protected glyph(p: string): string {
    return PIECE_GLYPH[p.toLowerCase()];
  }

  protected squareClass(sq: number): string {
    const dark = (Math.floor(sq / 8) + (sq % 8)) % 2 === 0;
    let cls = dark ? 'bg-emerald-700 text-emerald-50 dark:bg-emerald-900' : 'bg-emerald-50 text-emerald-800 dark:bg-slate-600 dark:text-slate-200';
    if (this.lastSquares().includes(sq)) cls += dark ? ' !bg-amber-600/80' : ' !bg-amber-200 dark:!bg-amber-700/60';
    if (this.selected() === sq) cls += ' ring-4 ring-inset ring-amber-400';
    if (this.kingInCheck() === sq) cls += ' !bg-rose-500';
    return cls;
  }

  protected label(sq: number): string {
    const p = this.board()[sq];
    const names: Record<string, string> = { k: 'king', q: 'queen', r: 'rook', b: 'bishop', n: 'knight', p: 'pawn' };
    return squareName(sq) + (p ? `, ${p === p.toUpperCase() ? 'white' : 'black'} ${names[p.toLowerCase()]}` : '');
  }

  protected fileLabel(sq: number): string | null {
    const bottomRow = this.flipped() ? Math.floor(sq / 8) === 7 : Math.floor(sq / 8) === 0;
    return bottomRow ? squareName(sq)[0] : null;
  }

  protected rankLabel(sq: number): string | null {
    const leftCol = this.flipped() ? sq % 8 === 7 : sq % 8 === 0;
    return leftCol ? squareName(sq)[1] : null;
  }

  protected tap(sq: number): void {
    if (!this.legal().length) return;
    const from = this.selected();
    if (from !== null && this.targets().has(sq)) {
      const uci = squareName(from) + squareName(sq);
      const options = this.legal().filter((m) => m.startsWith(uci));
      if (options.length > 1) this.promotion.set({ from, to: sq });
      else this.move.emit(options[0]);
      return;
    }
    const name = squareName(sq);
    this.selected.set(this.legal().some((m) => m.startsWith(name)) && from !== sq ? sq : null);
  }

  protected promote(piece: string): void {
    const p = this.promotion();
    if (!p) return;
    this.promotion.set(null);
    this.move.emit(squareName(p.from) + squareName(p.to) + piece);
  }
}
