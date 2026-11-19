import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { ThemeService } from './core/theme.service';
import { Footer } from './layout/footer';
import { Navbar } from './layout/navbar';
import { Toasts } from './layout/toasts';

@Component({
  selector: 'app-root',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterOutlet, Navbar, Footer, Toasts],
  template: `
    <a href="#main" class="sr-only z-50 rounded-xl bg-brand-600 px-4 py-2 text-white focus:not-sr-only focus:fixed focus:top-3 focus:left-3">Skip to content</a>
    <app-navbar />
    <main id="main" class="min-h-[calc(100dvh-4rem)]">
      <router-outlet />
    </main>
    <app-footer />
    <app-toasts />
  `,
})
export class App {
  constructor() {
    // Start following the saved / system colour theme.
    inject(ThemeService);
  }
}
