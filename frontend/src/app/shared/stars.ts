import { ChangeDetectionStrategy, Component, computed, input, model } from '@angular/core';
import { Icon } from './icon';

/** A compact rating: ★ 4.8 (12). Shows nothing until a member has a review. */
@Component({
  selector: 'app-stars',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [Icon],
  host: { class: 'inline-flex items-center gap-1 align-middle' },
  template: `
    @if (value() != null) {
      <app-icon name="star" [size]="size()" class="text-amber-500 [&_svg]:fill-current" />
      <span class="font-semibold">{{ value()!.toFixed(1) }}</span>
      @if (count() != null && showCount()) { <span class="text-slate-500 dark:text-slate-400">({{ count() }})</span> }
      <span class="sr-only">out of 5 stars from {{ count() }} {{ count() === 1 ? 'review' : 'reviews' }}</span>
    }
  `,
})
export class Stars {
  readonly value = input<number | null | undefined>(null);
  readonly count = input<number | null>(null);
  readonly size = input(14);
  readonly showCount = input(true);
}

const LABELS = ['', 'Bad', 'Not great', 'Okay', 'Good', 'Excellent'];

/** Pick 1 to 5 stars, with the arrow keys or a tap. */
@Component({
  selector: 'app-star-input',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [Icon],
  host: { class: 'inline-flex items-center gap-3' },
  template: `
    <span class="inline-flex" role="radiogroup" [attr.aria-label]="label()">
      @for (n of [1, 2, 3, 4, 5]; track n) {
        <button type="button" role="radio" class="rounded-lg p-1 transition hover:scale-110 focus-visible:ring-4 focus-visible:ring-brand-500/30 focus-visible:outline-none"
                [attr.aria-checked]="value() === n" [attr.aria-label]="n + (n === 1 ? ' star' : ' stars') + ', ' + labels[n]"
                [attr.tabindex]="(value() || 1) === n ? 0 : -1" (click)="value.set(n)" (keydown)="onKey($event)">
          <app-icon name="star" [size]="30" [class]="n <= (value() || 0) ? 'text-amber-500 [&_svg]:fill-current' : 'text-slate-300 dark:text-slate-600'" />
        </button>
      }
    </span>
    <span class="text-sm font-semibold text-slate-600 dark:text-slate-300" aria-live="polite">{{ caption() }}</span>
  `,
})
export class StarInput {
  readonly value = model(0);
  readonly label = input('Rating');
  protected readonly labels = LABELS;
  protected readonly caption = computed(() => LABELS[this.value()] ?? '');

  protected onKey(event: KeyboardEvent): void {
    const v = this.value() || 1;
    if (event.key === 'ArrowRight' || event.key === 'ArrowUp') {
      event.preventDefault();
      this.value.set(Math.min(5, v + 1));
    } else if (event.key === 'ArrowLeft' || event.key === 'ArrowDown') {
      event.preventDefault();
      this.value.set(Math.max(1, v - 1));
    }
  }
}
