import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { NavigationEnd, Router, RouterOutlet } from '@angular/router';
import { filter, map } from 'rxjs';
import { AuthService } from './core/auth.service';
import { FeaturesService } from './core/features.service';
import { ThemeService } from './core/theme.service';
import { MaintenancePage, SuspendedPage } from './features/status-pages';
import { Footer } from './layout/footer';
import { Navbar } from './layout/navbar';
import { Toasts } from './layout/toasts';

@Component({
  selector: 'app-root',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterOutlet, Navbar, Footer, Toasts, MaintenancePage, SuspendedPage],
  template: `
    @if (!inConsole()) {
      <a href="#main" (click)="skipToMain($event)" class="sr-only z-50 rounded-xl bg-brand-600 px-4 py-2 text-white focus:not-sr-only focus:fixed focus:top-3 focus:left-3">Skip to content</a>
      <app-navbar />
    }
    <main id="main" tabindex="-1" class="outline-none" [class]="inConsole() ? '' : 'min-h-[calc(100dvh-4rem)]'">
      @if (!inConsole() && auth.suspension(); as s) {
        <app-suspended-page [suspension]="s" />
      } @else if (showMaintenance()) {
        <app-maintenance-page />
      }
      <div [hidden]="!inConsole() && (!!auth.suspension() || showMaintenance())">
        <router-outlet />
      </div>
    </main>
    @if (!inConsole()) {
      <app-footer />
    }
    <app-toasts />
  `,
})
export class App {
  protected readonly auth = inject(AuthService);
  private readonly features = inject(FeaturesService);
  private readonly url = toSignal(
    inject(Router).events.pipe(
      filter((e) => e instanceof NavigationEnd),
      map((e) => (e as NavigationEnd).urlAfterRedirects),
    ),
    { initialValue: window.location.pathname },
  );

  /** The staff console has its own full-screen layout. */
  /** With the site's base URL, a plain "#main" link would load the home page; move focus instead. */
  protected skipToMain(event: Event): void {
    event.preventDefault();
    const main = document.getElementById('main');
    main?.focus();
    main?.scrollIntoView();
  }

  protected readonly inConsole = computed(() => this.url().startsWith('/admin'));
  /** Maintenance closes the member site, but login stays open so staff can reach the console. */
  protected readonly showMaintenance = computed(
    () => !this.inConsole() && this.features.maintenance() && !this.url().startsWith('/login'),
  );

  constructor() {
    // Start following the saved / system colour theme.
    inject(ThemeService);
  }
}
