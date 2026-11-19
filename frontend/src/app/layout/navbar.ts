import { ChangeDetectionStrategy, Component, ElementRef, inject, signal } from '@angular/core';
import { NavigationEnd, Router, RouterLink, RouterLinkActive } from '@angular/router';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { filter } from 'rxjs';
import { AuthService } from '../core/auth.service';
import { ThemeService } from '../core/theme.service';
import { Avatar } from '../shared/avatar';
import { Icon } from '../shared/icon';
import { Logo } from '../shared/logo';
import { PhonePipe } from '../shared/phone.pipe';

@Component({
  selector: 'app-navbar',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, RouterLinkActive, Logo, Icon, Avatar, PhonePipe],
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
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);

  protected readonly mobileOpen = signal(false);
  protected readonly accountOpen = signal(false);

  constructor() {
    inject(Router).events.pipe(filter((e) => e instanceof NavigationEnd), takeUntilDestroyed())
      .subscribe(() => this.closeMenus());
  }

  protected closeMenus(): void {
    this.mobileOpen.set(false);
    this.accountOpen.set(false);
  }

  protected onDocumentClick(event: MouseEvent): void {
    if (!this.host.nativeElement.contains(event.target as Node)) this.closeMenus();
  }

  protected logout(): void {
    this.closeMenus();
    void this.auth.logout();
  }
}
