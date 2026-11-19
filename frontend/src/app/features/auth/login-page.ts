import { ChangeDetectionStrategy, Component, DestroyRef, ElementRef, inject, input, signal, viewChild } from '@angular/core';
import { Router } from '@angular/router';
import { AuthService } from '../../core/auth.service';
import { problemOf } from '../../core/errors';
import { formatIndianMobile, isValidIndianMobile, safeReturnUrl } from '../../core/util';
import { Icon } from '../../shared/icon';
import { Logo } from '../../shared/logo';
import { OtpInput } from '../../shared/otp-input';

@Component({
  selector: 'app-login-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [Icon, Logo, OtpInput],
  templateUrl: './login-page.html',
})
export class LoginPage {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  /** Where to go after logging in (from ?returnUrl=…). */
  readonly returnUrl = input<string>();

  protected readonly step = signal<'phone' | 'code'>('phone');
  protected readonly phone = signal('');
  protected readonly code = signal('');
  protected readonly busy = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly devCode = signal<string | null>(null);
  protected readonly resendIn = signal(0);

  private readonly phoneField = viewChild<ElementRef<HTMLInputElement>>('phoneField');
  private readonly otp = viewChild(OtpInput);
  private timer: ReturnType<typeof setInterval> | undefined;

  constructor() {
    inject(DestroyRef).onDestroy(() => clearInterval(this.timer));
  }

  protected readonly isValid = isValidIndianMobile;

  protected onPhoneInput(el: HTMLInputElement): void {
    const formatted = formatIndianMobile(el.value);
    el.value = formatted;
    this.phone.set(formatted);
    this.error.set(null);
  }

  protected async sendCode(event?: Event): Promise<void> {
    event?.preventDefault();
    if (!isValidIndianMobile(this.phone())) {
      this.error.set('Please enter a valid 10-digit mobile number.');
      return;
    }
    this.busy.set(true);
    this.error.set(null);
    try {
      const res = await this.auth.requestOtp(this.phone());
      this.devCode.set(res.devCode);
      this.code.set('');
      this.step.set('code');
      this.startCountdown(res.resendInSeconds);
      setTimeout(() => this.otp()?.focus());
    } catch (e) {
      this.error.set(problemOf(e).detail);
    } finally {
      this.busy.set(false);
    }
  }

  protected async verify(): Promise<void> {
    if (this.code().length !== 6 || this.busy()) return;
    this.busy.set(true);
    this.error.set(null);
    try {
      const me = await this.auth.verifyOtp(this.phone(), this.code());
      const target = safeReturnUrl(this.returnUrl());
      if (me.profileComplete) {
        await this.router.navigateByUrl(target);
      } else {
        await this.router.navigate(['/onboarding'], { queryParams: { returnUrl: target } });
      }
    } catch (e) {
      this.error.set(problemOf(e).detail);
      this.otp()?.clear();
      setTimeout(() => this.otp()?.focus());
    } finally {
      this.busy.set(false);
    }
  }

  protected changeNumber(): void {
    this.step.set('phone');
    this.error.set(null);
    this.devCode.set(null);
    setTimeout(() => this.phoneField()?.nativeElement.focus());
  }

  protected formatCountdown(seconds: number): string {
    return `0:${String(seconds).padStart(2, '0')}`;
  }

  private startCountdown(seconds: number): void {
    clearInterval(this.timer);
    this.resendIn.set(seconds);
    this.timer = setInterval(() => {
      this.resendIn.update((s) => Math.max(0, s - 1));
      if (this.resendIn() === 0) clearInterval(this.timer);
    }, 1000);
  }
}
