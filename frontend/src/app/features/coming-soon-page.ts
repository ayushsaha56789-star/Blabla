import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { RouterLink } from '@angular/router';
import { featurePage } from '../core/feature-pages';
import { Icon } from '../shared/icon';

/** Shown for an area the owner has switched on before it is fully built. */
@Component({
  selector: 'app-coming-soon-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, Icon],
  template: `
    @if (page(); as p) {
      <div class="container-page py-10 sm:py-16">
        <section class="relative mx-auto max-w-3xl overflow-hidden rounded-[2rem] bg-linear-to-br p-8 text-white shadow-2xl animate-fade-up sm:p-12" [class]="p.gradient">
          <div aria-hidden="true" class="absolute -top-24 -right-24 h-72 w-72 rounded-full bg-white/20 blur-3xl"></div>
          <div class="relative">
            <span class="badge bg-white/20 text-white backdrop-blur"><app-icon name="sparkles" [size]="13" /> Coming soon</span>
            <span class="mt-6 flex h-16 w-16 items-center justify-center rounded-3xl bg-white/20 backdrop-blur">
              <app-icon [name]="p.icon" [size]="32" />
            </span>
            <h1 class="mt-5 text-4xl font-extrabold tracking-tight text-balance sm:text-5xl">{{ p.label }}</h1>
            <p class="mt-3 max-w-xl text-lg text-white/90">{{ p.tagline }}</p>
          </div>
        </section>
        <section class="card mx-auto -mt-6 max-w-2xl p-6 animate-fade-up [animation-delay:120ms] sm:p-8">
          <h2 class="font-bold">What's coming</h2>
          <ul class="mt-4 space-y-3">
            @for (point of p.points; track point) {
              <li class="flex items-start gap-3"><app-icon name="checkCircle" [size]="20" class="mt-0.5 text-brand-600 dark:text-brand-400" /> {{ point }}</li>
            }
          </ul>
          <div class="mt-8 flex flex-wrap gap-3">
            <a routerLink="/search" class="btn btn-primary"><app-icon name="search" [size]="18" /> Find a ride meanwhile</a>
            <a routerLink="/" class="btn btn-secondary">Back to home</a>
          </div>
        </section>
      </div>
    }
  `,
})
export class ComingSoonPage {
  /** From the route data. */
  readonly feature = input.required<string>();
  protected readonly page = computed(() => featurePage(this.feature()));
}
