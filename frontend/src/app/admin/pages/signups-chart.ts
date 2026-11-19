import { ChangeDetectionStrategy, Component, computed, input, signal } from '@angular/core';
import { DailyCount } from '../admin.models';

/**
 * Daily sign-ups as columns. Follows the dataviz rules: one series (so no legend; the title names it), columns at
 * most 24px wide with a 4px rounded top and a square base, 2px air between columns, a hairline baseline, the latest
 * value labelled at its cap, a tooltip on hover and keyboard focus, and a table view. Mark colours were checked with
 * the palette validator against both card surfaces (#059669 light, #12a877 dark).
 */
@Component({
  selector: 'app-signups-chart',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="relative mt-5 h-44 pl-7" role="group" [attr.aria-label]="summary()">
      <span class="absolute top-0 left-0 text-[0.7rem] font-medium text-slate-400 tabular-nums">{{ max() }}</span>
      <span class="absolute bottom-0 left-0 text-[0.7rem] font-medium text-slate-400 tabular-nums">0</span>
      <div class="absolute inset-x-0 top-2 ml-7 border-t border-slate-100 dark:border-slate-800" aria-hidden="true"></div>

      <div class="absolute top-2 right-0 bottom-0 left-7 flex items-end gap-[2px] border-b border-slate-200 dark:border-slate-700">
        @for (d of days(); track d.day; let i = $index; let last = $last) {
          <button type="button"
                  class="group relative flex h-full flex-1 cursor-default items-end justify-center rounded-md focus-visible:bg-slate-100 focus-visible:outline-none dark:focus-visible:bg-slate-800"
                  [attr.aria-label]="dayLabel(d.day) + ': ' + d.count + (d.count === 1 ? ' sign-up' : ' sign-ups')"
                  (mouseenter)="hovered.set(i)" (mouseleave)="hovered.set(null)" (focus)="hovered.set(i)" (blur)="hovered.set(null)">
            @if (d.count > 0) {
              <span class="block w-full max-w-6 rounded-t-[4px] bg-chart-light transition-opacity dark:bg-chart-dark"
                    [class.opacity-70]="hovered() !== null && hovered() !== i" [style.height.%]="percent(d.count)"></span>
            }
            @if (last && d.count > 0) {
              <span class="pointer-events-none absolute mb-1 text-xs font-bold text-slate-700 tabular-nums dark:text-slate-200"
                    [style.bottom.%]="percent(d.count)">{{ d.count }}</span>
            }
          </button>
        }
      </div>

      @if (tip(); as t) {
        <div class="pointer-events-none absolute z-10 -translate-x-1/2 -translate-y-full rounded-xl bg-slate-900 px-3 py-2 text-center whitespace-nowrap text-white shadow-xl dark:bg-white dark:text-slate-900"
             [style.left]="t.left" [style.top]="t.top">
          <p class="text-sm font-bold tabular-nums">{{ t.count }} {{ t.count === 1 ? 'sign-up' : 'sign-ups' }}</p>
          <p class="text-[0.7rem] opacity-70">{{ t.label }}</p>
        </div>
      }
    </div>
    <div class="mt-2 flex justify-between pl-7 text-[0.7rem] font-medium text-slate-400">
      <span>{{ dayLabel(days()[0]?.day) }}</span>
      <span>Today</span>
    </div>
    <details class="mt-3 text-sm">
      <summary class="cursor-pointer text-xs font-semibold text-slate-500 hover:text-slate-800 dark:text-slate-400 dark:hover:text-white">Show as a table</summary>
      <table class="data-table mt-2">
        <thead><tr><th>Day</th><th class="text-right">Sign-ups</th></tr></thead>
        <tbody>
          @for (d of days(); track d.day) {
            <tr><td>{{ dayLabel(d.day) }}</td><td class="text-right tabular-nums">{{ d.count }}</td></tr>
          }
        </tbody>
      </table>
    </details>
  `,
})
export class SignupsChart {
  readonly days = input.required<DailyCount[]>();
  protected readonly hovered = signal<number | null>(null);

  /** A clean axis maximum: 1, 2, 5, 10, 20, 50 … at or above the highest day. */
  protected readonly max = computed(() => {
    const top = Math.max(1, ...this.days().map((d) => d.count));
    const pow = Math.pow(10, Math.floor(Math.log10(top)));
    for (const step of [1, 2, 5, 10]) {
      if (step * pow >= top) return step * pow;
    }
    return 10 * pow;
  });

  protected readonly summary = computed(() => {
    const total = this.days().reduce((s, d) => s + d.count, 0);
    return `Sign-ups per day, last ${this.days().length} days: ${total} in total.`;
  });

  /** Tooltip for the hovered or focused column, placed just above its bar. */
  protected readonly tip = computed(() => {
    const i = this.hovered();
    if (i === null) return null;
    const d = this.days()[i];
    const n = this.days().length;
    // The plot starts 1.75rem from the left and 0.5rem from the top (see the template).
    return {
      count: d.count,
      label: this.dayLabel(d.day),
      left: `calc(1.75rem + (100% - 1.75rem) * ${(i + 0.5) / n})`,
      top: `calc(0.5rem + (100% - 0.5rem) * ${1 - this.percent(d.count) / 100} - 6px)`,
    };
  });

  protected percent(count: number): number {
    return (count / this.max()) * 100;
  }

  protected dayLabel(day: string | undefined): string {
    if (!day) return '';
    const [y, m, d] = day.split('-').map(Number);
    return new Date(y, m - 1, d).toLocaleDateString('en-IN', { weekday: 'short', day: 'numeric', month: 'short' });
  }
}
