import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, inject, input } from '@angular/core';
import { RouterLink } from '@angular/router';
import { AuthService, Suspension } from '../core/auth.service';
import { Icon } from '../shared/icon';

/** Shown to everyone (except on the login page and the console) while maintenance mode is on. */
@Component({
  selector: 'app-maintenance-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, Icon],
  template: `
    <div class="container-page flex min-h-[70dvh] flex-col items-center justify-center py-16 text-center">
      <span class="flex h-20 w-20 items-center justify-center rounded-[1.75rem] bg-accent-100 text-accent-600 animate-pop dark:bg-accent-600/15 dark:text-accent-300">
        <app-icon name="refresh" [size]="38" />
      </span>
      <h1 class="mt-6 text-3xl font-extrabold tracking-tight sm:text-4xl">We'll be right back</h1>
      <p class="mt-3 max-w-md text-slate-600 dark:text-slate-400">
        SaathChalo is having a short break for maintenance. Your rides and profile are safe — please check back in a little while.
      </p>
      @if (auth.staff()) {
        <a routerLink="/admin" class="btn btn-secondary mt-8"><app-icon name="lock" [size]="16" /> Staff: open the console</a>
      }
    </div>
  `,
})
export class MaintenancePage {
  protected readonly auth = inject(AuthService);
}

/** Shown when the account is suspended, with the reason staff gave. */
@Component({
  selector: 'app-suspended-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [DatePipe, Icon],
  template: `
    <div class="container-page flex min-h-[70dvh] flex-col items-center justify-center py-16 text-center">
      <span class="flex h-20 w-20 items-center justify-center rounded-[1.75rem] bg-rose-50 text-rose-600 animate-pop dark:bg-rose-500/15 dark:text-rose-300">
        <app-icon name="ban" [size]="38" />
      </span>
      <h1 class="mt-6 text-3xl font-extrabold tracking-tight">Your account is suspended</h1>
      <p class="mt-3 max-w-md text-slate-600 dark:text-slate-400">
        @if (suspension().until) { Until {{ suspension().until | date: 'd MMMM y' }}. } @else { Until further notice. }
      </p>
      @if (suspension().reason) {
        <blockquote class="card mt-6 max-w-md p-5 text-left text-sm">
          <p class="kicker">Reason</p>
          <p class="mt-1">{{ suspension().reason }}</p>
        </blockquote>
      }
      <p class="mt-6 max-w-md text-sm text-slate-500">If you think this is a mistake, contact SaathChalo support.</p>
      <button type="button" class="btn btn-secondary mt-6" (click)="auth.logout()"><app-icon name="logout" [size]="16" /> Log out</button>
    </div>
  `,
})
export class SuspendedPage {
  protected readonly auth = inject(AuthService);
  readonly suspension = input.required<Suspension>();
}
