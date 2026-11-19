import { ChangeDetectionStrategy, Component, computed, inject, OnInit, signal, viewChild } from '@angular/core';
import { RouterLink } from '@angular/router';
import { problemOf } from '../../core/errors';
import { ToastService } from '../../core/toast.service';
import { Icon } from '../../shared/icon';
import { Logo } from '../../shared/logo';
import { OtpInput } from '../../shared/otp-input';
import { TotpSetup } from '../admin.models';
import { downloadText } from '../admin-ui';
import { ConsoleSessionService } from '../console-session.service';

/** The door to the console: set up two-step verification once, then a 6-digit code every time. */
@Component({
  selector: 'app-staff-gate',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [Icon, Logo, OtpInput, RouterLink],
  templateUrl: './staff-gate.html',
})
export class StaffGate implements OnInit {
  protected readonly session = inject(ConsoleSessionService);
  private readonly toast = inject(ToastService);

  protected readonly setup = signal<TotpSetup | null>(null);
  protected readonly code = signal('');
  protected readonly recovery = signal('');
  protected readonly useRecovery = signal(false);
  protected readonly busy = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly saved = signal(false);
  private readonly otp = viewChild(OtpInput);

  protected readonly mode = computed(() => {
    if (this.session.freshCodes()) return 'codes';
    return this.session.status()?.totpEnabled ? 'signin' : 'setup';
  });

  ngOnInit(): void {
    if (this.mode() === 'setup') void this.beginSetup();
    else setTimeout(() => this.otp()?.focus());
  }

  protected async beginSetup(): Promise<void> {
    this.error.set(null);
    try {
      this.setup.set(await this.session.startSetup());
      setTimeout(() => this.otp()?.focus());
    } catch (e) {
      this.error.set(problemOf(e).detail);
    }
  }

  protected async confirmSetup(): Promise<void> {
    if (this.code().length !== 6 || this.busy()) return;
    await this.run(() => this.session.confirmSetup(this.code()));
  }

  protected async signIn(): Promise<void> {
    if (this.busy()) return;
    if (this.useRecovery()) {
      if (this.recovery().trim().length < 10) return;
      await this.run(() => this.session.signIn(null, this.recovery().trim()));
    } else {
      if (this.code().length !== 6) return;
      await this.run(() => this.session.signIn(this.code(), null));
    }
  }

  protected toggleRecovery(): void {
    this.useRecovery.update((v) => !v);
    this.error.set(null);
    if (!this.useRecovery()) setTimeout(() => this.otp()?.focus());
  }

  protected async copyCodes(): Promise<void> {
    try {
      await navigator.clipboard.writeText(this.session.freshCodes()!.join('\n'));
      this.toast.success('Recovery codes copied.');
    } catch {
      this.toast.error('Copying is blocked here. Please download the codes instead.');
    }
  }

  protected downloadCodes(): void {
    downloadText('saathchalo-recovery-codes.txt',
      'SaathChalo staff recovery codes. Each code works once.\n\n' + this.session.freshCodes()!.join('\n') + '\n');
  }

  protected finish(): void {
    this.session.freshCodes.set(null);
  }

  private async run(action: () => Promise<unknown>): Promise<void> {
    this.busy.set(true);
    this.error.set(null);
    try {
      await action();
    } catch (e) {
      const p = problemOf(e);
      this.error.set(p.detail);
      this.code.set('');
      this.otp()?.clear();
      setTimeout(() => this.otp()?.focus());
    } finally {
      this.busy.set(false);
    }
  }
}
