import { ChangeDetectionStrategy, Component, effect, inject, signal, viewChild } from '@angular/core';
import { Icon } from '../../shared/icon';
import { OtpInput } from '../../shared/otp-input';
import { ConfirmCodeService } from '../confirm-code.service';

/** "Confirm it's you" — asks for a fresh authenticator code before a sensitive console action. */
@Component({
  selector: 'app-confirm-code-dialog',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [Icon, OtpInput],
  host: { '(document:keydown.escape)': 'confirm.open() && confirm.cancel()' },
  template: `
    @if (confirm.open()) {
      <div class="fixed inset-0 z-[60] flex items-end justify-center bg-slate-950/50 p-4 backdrop-blur-sm sm:items-center"
           (click)="confirm.cancel()">
        <div class="card w-full max-w-sm animate-pop p-6 shadow-2xl sm:p-8" role="dialog" aria-modal="true"
             aria-labelledby="confirm-title" (click)="$event.stopPropagation()">
          <span class="flex h-12 w-12 items-center justify-center rounded-2xl bg-brand-50 text-brand-700 dark:bg-brand-500/15 dark:text-brand-300">
            <app-icon name="lock" [size]="24" />
          </span>
          <h2 id="confirm-title" class="mt-4 text-xl font-extrabold tracking-tight">Confirm it's you</h2>
          <p class="mt-1 text-sm text-slate-600 dark:text-slate-400">
            This is a sensitive change. Enter the current 6-digit code from your authenticator app.
          </p>
          <app-otp-input class="mt-5" [(value)]="code" (completed)="submit()" [invalid]="!!confirm.error()" />
          @if (confirm.error()) {
            <p class="field-error" role="alert">{{ confirm.error() }}</p>
          }
          <div class="mt-6 flex gap-3">
            <button type="button" class="btn btn-secondary flex-1" (click)="confirm.cancel()">Cancel</button>
            <button type="button" class="btn btn-primary flex-1" (click)="submit()" [disabled]="code().length !== 6">Confirm</button>
          </div>
        </div>
      </div>
    }
  `,
})
export class ConfirmCodeDialog {
  protected readonly confirm = inject(ConfirmCodeService);
  protected readonly code = signal('');
  private readonly otp = viewChild(OtpInput);

  constructor() {
    effect(() => {
      if (this.confirm.open()) {
        this.code.set('');
        setTimeout(() => this.otp()?.focus());
      }
    });
  }

  protected submit(): void {
    if (this.code().length === 6) this.confirm.submit(this.code());
  }
}
