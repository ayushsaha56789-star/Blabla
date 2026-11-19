import { ChangeDetectionStrategy, Component } from '@angular/core';
import { RouterLink } from '@angular/router';
import { POPULAR_ROUTES } from '../core/cities';
import { Logo } from '../shared/logo';

@Component({
  selector: 'app-footer',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, Logo],
  host: { class: 'block border-t border-slate-200 bg-white dark:border-slate-800 dark:bg-slate-950' },
  template: `
    <div class="container-page grid gap-10 py-12 sm:grid-cols-2 lg:grid-cols-4">
      <div class="lg:col-span-2">
        <app-logo [size]="30" />
        <p class="mt-3 max-w-sm text-sm text-slate-500 dark:text-slate-400">
          Share rides between cities with verified people. Split the cost, make the trip more fun,
          and put fewer cars on India's roads.
        </p>
      </div>
      <div>
        <h2 class="text-sm font-bold">SaathChalo</h2>
        <ul class="mt-3 space-y-2 text-sm text-slate-500 dark:text-slate-400">
          <li><a routerLink="/search" class="hover:text-brand-600 dark:hover:text-brand-400">Find a ride</a></li>
          <li><a routerLink="/publish" class="hover:text-brand-600 dark:hover:text-brand-400">Offer a ride</a></li>
          <li><a routerLink="/profile" class="hover:text-brand-600 dark:hover:text-brand-400">My profile</a></li>
        </ul>
      </div>
      <div>
        <h2 class="text-sm font-bold">Popular routes</h2>
        <ul class="mt-3 space-y-2 text-sm text-slate-500 dark:text-slate-400">
          @for (r of routes; track $index) {
            <li>
              <a routerLink="/search" [queryParams]="{ from: r[0], to: r[1] }" class="hover:text-brand-600 dark:hover:text-brand-400">
                {{ r[0] }} → {{ r[1] }}
              </a>
            </li>
          }
        </ul>
      </div>
    </div>
    <div class="border-t border-slate-100 dark:border-slate-900">
      <p class="container-page py-5 text-xs text-slate-400">© {{ year }} SaathChalo · Made with ❤️ in India</p>
    </div>
  `,
})
export class Footer {
  protected readonly routes = POPULAR_ROUTES.slice(0, 4);
  protected readonly year = new Date().getFullYear();
}
