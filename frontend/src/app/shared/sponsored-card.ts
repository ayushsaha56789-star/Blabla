import { ChangeDetectionStrategy, Component, inject, input } from '@angular/core';
import { CATEGORY_LABELS, PromoCard, PromotionsApi } from '../core/promotions-api';
import { Icon } from './icon';

/** A member's promotion, always labelled "Sponsored". The link opens in a new tab; the click is counted once a day. */
@Component({
  selector: 'app-sponsored-card',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [Icon],
  host: { class: 'block min-w-0' },
  template: `
    <article class="card overflow-hidden border-2 border-dashed border-lime-300/70 dark:border-lime-500/30" [attr.aria-label]="'Sponsored: ' + card().business">
      <div class="flex gap-4 p-4">
        @if (card().imageUrl) { <img [src]="card().imageUrl" alt="" class="h-20 w-20 shrink-0 rounded-2xl object-cover sm:h-24 sm:w-24" loading="lazy" /> }
        <div class="min-w-0 flex-1">
          <p class="flex items-center gap-2 text-xs font-semibold text-slate-500 dark:text-slate-400">
            <span class="rounded-full bg-lime-100 px-2 py-0.5 text-[0.7rem] font-bold tracking-wide text-lime-800 uppercase dark:bg-lime-500/20 dark:text-lime-200">Sponsored</span>
            <span class="line-clamp-1 min-w-0 break-all" translate="no">{{ card().business }} · {{ category() }}</span>
          </p>
          <p class="mt-1 font-bold" translate="no">{{ card().title }}</p>
          <p class="mt-0.5 line-clamp-3 text-sm text-slate-600 dark:text-slate-400" translate="no">{{ card().body }}</p>
          @if (card().linkUrl) {
            <a [href]="card().linkUrl" target="_blank" rel="noopener noreferrer sponsored" class="btn btn-secondary btn-sm mt-2" (click)="clicked()">
              Visit <app-icon name="external" [size]="14" />
            </a>
          }
        </div>
      </div>
    </article>
  `,
})
export class SponsoredCard {
  private readonly api = inject(PromotionsApi);
  readonly card = input.required<PromoCard>();
  /** In previews nothing is counted. */
  readonly preview = input(false);

  protected category(): string {
    return CATEGORY_LABELS[this.card().category];
  }

  protected clicked(): void {
    if (!this.preview()) void this.api.click(this.card().id).catch(() => undefined);
  }
}
