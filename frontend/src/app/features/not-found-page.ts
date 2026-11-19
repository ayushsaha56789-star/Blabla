import { ChangeDetectionStrategy, Component } from '@angular/core';
import { RouterLink } from '@angular/router';

@Component({
  selector: 'app-not-found-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink],
  template: `
    <div class="container-page flex min-h-[60dvh] flex-col items-center justify-center py-16 text-center">
      <span class="text-6xl" aria-hidden="true">🛣️</span>
      <h1 class="mt-5 text-3xl font-extrabold tracking-tight">Looks like a wrong turn</h1>
      <p class="mt-2 text-slate-600 dark:text-slate-400">We couldn't find that page.</p>
      <a routerLink="/" class="btn btn-primary mt-8">Back to home</a>
    </div>
  `,
})
export class NotFoundPage {}
