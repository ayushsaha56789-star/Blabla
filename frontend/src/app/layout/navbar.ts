import { ChangeDetectionStrategy, Component, computed, ElementRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { NavigationEnd, Router, RouterLink, RouterLinkActive } from '@angular/router';
import { filter } from 'rxjs';
import { AuthService } from '../core/auth.service';
import { FEATURE_PAGES } from '../core/feature-pages';
import { FeaturesService } from '../core/features.service';
import { ThemeService } from '../core/theme.service';
import { TripsService } from '../core/trips.service';
import { Avatar } from '../shared/avatar';
import { Icon } from '../shared/icon';
import { Logo } from '../shared/logo';
import { PhonePipe } from '../shared/phone.pipe';
import { ThemeSwitcher } from '../shared/theme-switcher';

type Menu = 'account' | 'explore' | 'theme';

@Component({
  selector: 'app-navbar',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, RouterLinkActive, Logo, Icon, Avatar, PhonePipe, ThemeSwitcher],
  host: {
    class: 'sticky top-0 z-40 block border-b border-slate-200/70 bg-white/80 backdrop-blur-xl dark:border-slate-800/70 dark:bg-slate-950/80',
    '(document:click)': 'onDocumentClick($event)',
    '(document:keydown.escape)': 'closeMenus()',
  },
  templateUrl: './navbar.html',
})
export class Navbar {
  protected readonly auth = inject(AuthService);
  protected readonly theme = inject(ThemeService);
  protected readonly features = inject(FeaturesService);
  protected readonly trips = inject(TripsService);
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);

  protected readonly mobileOpen = signal(false);
  protected readonly openMenu = signal<Menu | null>(null);

  /** Extra areas the owner has switched on. Games gets its own top-level link; the rest go under "Explore". */
  protected readonly extras = computed(() => FEATURE_PAGES.filter((p) => this.features.enabledKeys().has(p.key)));
  protected readonly gamesOn = computed(() => this.features.enabledKeys().has('games'));
  protected readonly exploreItems = computed(() => this.extras().filter((p) => p.key !== 'games'));

  constructor() {
    inject(Router).events.pipe(filter((e) => e instanceof NavigationEnd), takeUntilDestroyed())
      .subscribe(() => this.closeMenus());
  }

  protected toggle(menu: Menu): void {
    this.openMenu.update((m) => (m === menu ? null : menu));
  }

  protected closeMenus(): void {
    this.mobileOpen.set(false);
    this.openMenu.set(null);
  }

  protected onDocumentClick(event: MouseEvent): void {
    if (!this.host.nativeElement.contains(event.target as Node)) this.closeMenus();
  }

  protected logout(): void {
    this.closeMenus();
    void this.auth.logout();
  }
}
