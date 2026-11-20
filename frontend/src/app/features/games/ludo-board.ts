import { ChangeDetectionStrategy, Component, computed, input, output } from '@angular/core';
import { HOME_COLUMN, LUDO_COLOURS, LudoView, ludoCell, SAFE_SQUARES, TRACK, YARD_ORIGIN } from '../../core/games-api';

const FILL = ['bg-orange-500', 'bg-emerald-500', 'bg-sky-500', 'bg-violet-500'];
const SOFT = ['bg-orange-200 dark:bg-orange-500/40', 'bg-emerald-200 dark:bg-emerald-500/40', 'bg-sky-200 dark:bg-sky-500/40',
  'bg-violet-200 dark:bg-violet-500/40'];
const STEP = 100 / 15;

interface Piece {
  colour: number;
  token: number;
  top: number;
  left: number;
  size: number;
  movable: boolean;
  last: boolean;
}

/** A ludo board drawn on a 15 × 15 grid. Your tokens that can move pulse; tap one to move it. */
@Component({
  selector: 'app-ludo-board',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'block' },
  template: `
    <div class="relative aspect-square w-full overflow-hidden rounded-2xl border-4 border-slate-800 bg-white shadow-xl select-none [container-type:inline-size] dark:border-slate-950 dark:bg-slate-800">
      @for (y of yards; track y.colour) {
        <div class="absolute flex items-center justify-center" [class]="fill[y.colour]" [style]="box(y.row, y.col, 6, 6)">
          <div class="h-[66%] w-[66%] rounded-[14%] bg-white/90 dark:bg-slate-900/80"></div>
        </div>
      }
      @for (c of cells; track $index) {
        <div class="absolute border border-slate-300/80 dark:border-slate-600" [class]="c.cls" [style]="box(c.row, c.col, 1, 1)">
          @if (c.star) { <span class="absolute inset-0 flex items-center justify-center text-slate-400 dark:text-slate-400" style="font-size: 4cqw">★</span> }
        </div>
      }
      <div class="absolute" [style]="box(6, 6, 3, 3) + ';background: conic-gradient(from 45deg, #0ea5e9 0 25%, #8b5cf6 0 50%, #f97316 0 75%, #10b981 0 100%)'"></div>
      @for (p of pieces(); track p.colour * 4 + p.token) {
        <button type="button" class="absolute flex items-center justify-center transition-all duration-300" [style]="pieceBox(p)"
                [disabled]="!p.movable" (click)="token.emit(p.token)" [attr.aria-label]="colours[p.colour] + ' token ' + (p.token + 1) + (p.movable ? ', can move' : '')">
          <span class="block h-[78%] w-[78%] rounded-full border-[0.5cqw] border-white shadow-md dark:border-slate-200" [class]="fill[p.colour]"
                [class.animate-bounce]="p.movable" [class.ring-4]="p.movable || p.last" [class.ring-amber-400]="p.movable" [class.ring-white]="p.last && !p.movable"></span>
        </button>
      }
    </div>
  `,
})
export class LudoBoard {
  readonly view = input.required<LudoView>();
  readonly mySeat = input<number | null>(null);
  readonly token = output<number>();

  protected readonly fill = FILL;
  protected readonly colours = LUDO_COLOURS;
  protected readonly yards = YARD_ORIGIN.map(([row, col], colour) => ({ row, col, colour }));
  /** Track squares (start squares coloured, stars marked) and the four home columns. */
  protected readonly cells = [
    ...TRACK.map(([row, col], i) => ({
      row, col, star: SAFE_SQUARES.includes(i) && i % 13 !== 0,
      cls: i % 13 === 0 ? FILL[i / 13] : 'bg-white dark:bg-slate-700',
    })),
    ...HOME_COLUMN.flatMap((column, colour) => column.map(([row, col]) => ({ row, col, star: false, cls: SOFT[colour] }))),
  ];

  protected readonly pieces = computed(() => {
    const v = this.view();
    const raw: { colour: number; token: number; cell: [number, number] }[] = [];
    for (const colour of v.seats) {
      v.tokens[colour].forEach((progress, token) => raw.push({ colour, token, cell: ludoCell(colour, token, progress) }));
    }
    // Tokens sharing a square are drawn smaller, side by side.
    const groups = new Map<string, number>();
    const index = raw.map((r) => {
      const key = r.cell.join(',');
      const i = groups.get(key) ?? 0;
      groups.set(key, i + 1);
      return i;
    });
    const last = v.lastMove;
    return raw.map((r, i): Piece => {
      const count = groups.get(r.cell.join(',')) ?? 1;
      const size = count === 1 ? 1 : 0.55;
      const offsets = [[0, 0], [0.45, 0.45], [0, 0.45], [0.45, 0]];
      const [dy, dx] = count === 1 ? [0, 0] : offsets[index[i] % 4];
      return {
        colour: r.colour, token: r.token, top: r.cell[0] + dy, left: r.cell[1] + dx, size,
        movable: r.colour === this.mySeat() && v.movable.includes(r.token),
        last: !!last && last[0] === r.colour && last[1] === r.token,
      };
    });
  });

  protected box(row: number, col: number, h: number, w: number): string {
    return `top:${row * STEP}%;left:${col * STEP}%;height:${h * STEP}%;width:${w * STEP}%`;
  }

  protected pieceBox(p: Piece): string {
    return `top:${p.top * STEP}%;left:${p.left * STEP}%;height:${p.size * STEP}%;width:${p.size * STEP}%;z-index:${p.movable ? 3 : 2}`;
  }
}
