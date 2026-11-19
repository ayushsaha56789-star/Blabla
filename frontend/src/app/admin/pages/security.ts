import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { problemOf } from '../../core/errors';
import { ToastService } from '../../core/toast.service';
import { Icon } from '../../shared/icon';
import { ThemeSwitcher } from '../../shared/theme-switcher';
import { AdminApi } from '../admin-api';
import { downloadText } from '../admin-ui';
import { ConfirmationCancelled } from '../confirm-code.service';
import { ConsoleSessionService } from '../console-session.service';

@Component({
  selector: 'app-admin-security',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [Icon, ThemeSwitcher],
  template: `
    <header>
      <p class="kicker">Your account</p>
      <h1 class="mt-1 text-3xl font-extrabold tracking-tight">My security</h1>
    </header>

    <div class="mt-6 grid gap-4 lg:grid-cols-2">
      <section class="card p-6">
        <div class="flex items-start gap-3">
          <span class="flex h-11 w-11 items-center justify-center rounded-2xl bg-brand-50 text-brand-700 dark:bg-brand-500/15 dark:text-brand-300">
            <app-icon name="lock" [size]="22" />
          </span>
          <div>
            <h2 class="font-bold">Two-step verification is on</h2>
            <p class="mt-1 text-sm text-slate-600 dark:text-slate-400">
              The console asks for a code from your authenticator app at every sign-in, after 15 minutes without activity,
              and before sensitive changes.
            </p>
          </div>
        </div>
      </section>

      <section class="card p-6">
        <h2 class="font-bold">Recovery codes</h2>
        <p class="mt-1 text-sm text-slate-600 dark:text-slate-400">
          You have <b class="text-slate-900 dark:text-white">{{ session.status()?.recoveryCodesLeft ?? 0 }}</b> unused codes for when your phone is lost.
          Creating new codes makes the old ones stop working.
        </p>
        @if (codes(); as list) {
          <ul class="mt-4 grid grid-cols-2 gap-2 rounded-2xl bg-slate-50 p-4 font-mono text-sm font-semibold tracking-wider dark:bg-slate-800/60">
            @for (c of list; track c) { <li class="text-center">{{ c }}</li> }
          </ul>
          <button type="button" class="btn btn-secondary btn-sm mt-3" (click)="download(list)"><app-icon name="download" [size]="16" /> Download</button>
        } @else {
          <button type="button" class="btn btn-secondary btn-sm mt-4" (click)="regenerate()" [disabled]="busy()">
            <app-icon name="refresh" [size]="16" /> Create new recovery codes
          </button>
        }
      </section>

      <section class="card p-6">
        <h2 class="font-bold">Appearance</h2>
        <p class="mt-1 text-sm text-slate-600 dark:text-slate-400">White, black, or follow your device.</p>
        <app-theme-switcher class="mt-4 inline-block" [showLabels]="true" />
      </section>

      <section class="card p-6">
        <h2 class="font-bold">Sign out of the console</h2>
        <p class="mt-1 text-sm text-slate-600 dark:text-slate-400">You stay logged in to SaathChalo as a member.</p>
        <button type="button" class="btn btn-secondary btn-sm mt-4 text-rose-600 dark:text-rose-400" (click)="signOut()">
          <app-icon name="logout" [size]="16" /> Sign out of the console
        </button>
      </section>
    </div>
  `,
})
export class SecurityPage {
  protected readonly session = inject(ConsoleSessionService);
  private readonly api = inject(AdminApi);
  private readonly toast = inject(ToastService);
  private readonly router = inject(Router);

  protected readonly codes = signal<string[] | null>(null);
  protected readonly busy = signal(false);

  protected async regenerate(): Promise<void> {
    this.busy.set(true);
    try {
      const res = await this.api.newRecoveryCodes();
      this.codes.set(res.recoveryCodes);
      this.session.status.update((s) => (s ? { ...s, recoveryCodesLeft: res.recoveryCodes.length } : s));
      this.toast.success('New recovery codes created. Save them now — they will not be shown again.');
    } catch (e) {
      if (!(e instanceof ConfirmationCancelled)) this.toast.error(problemOf(e).detail);
    } finally {
      this.busy.set(false);
    }
  }

  protected download(codes: string[]): void {
    downloadText('saathchalo-recovery-codes.txt', 'SaathChalo staff recovery codes. Each code works once.\n\n' + codes.join('\n') + '\n');
  }

  protected async signOut(): Promise<void> {
    await this.session.signOut();
    await this.router.navigateByUrl('/');
  }
}
