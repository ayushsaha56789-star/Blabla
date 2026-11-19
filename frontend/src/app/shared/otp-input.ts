import { ChangeDetectionStrategy, Component, ElementRef, input, model, output, signal, viewChild } from '@angular/core';

/**
 * Six boxes for a one-time code. One real (invisible) input sits on top, which keeps paste and the
 * phone's "fill code from SMS" suggestion working.
 */
@Component({
  selector: 'app-otp-input',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'block' },
  template: `
    <div class="relative">
      <input #field type="text" inputmode="numeric" autocomplete="one-time-code" maxlength="6" spellcheck="false"
             class="absolute inset-0 z-10 h-full w-full cursor-text text-base opacity-0"
             aria-label="6-digit code" [value]="value()" [disabled]="disabled()"
             (input)="onInput(field)" (focus)="focused.set(true)" (blur)="focused.set(false)" />
      <div class="grid grid-cols-6 gap-2 sm:gap-3" aria-hidden="true">
        @for (i of slots; track i) {
          <div class="flex aspect-[4/5] items-center justify-center rounded-2xl border-2 bg-white text-2xl font-bold transition
                      dark:bg-slate-900"
               [class]="boxClass(i)">
            @if (value()[i]; as digit) {
              <span class="animate-pop">{{ digit }}</span>
            } @else if (focused() && i === value().length) {
              <span class="h-7 w-0.5 animate-pulse rounded bg-brand-500"></span>
            }
          </div>
        }
      </div>
    </div>
  `,
})
export class OtpInput {
  readonly value = model('');
  readonly disabled = input(false);
  readonly invalid = input(false);
  readonly completed = output<string>();

  protected readonly slots = [0, 1, 2, 3, 4, 5];
  protected readonly focused = signal(false);
  private readonly field = viewChild.required<ElementRef<HTMLInputElement>>('field');

  focus(): void {
    this.field().nativeElement.focus();
  }

  clear(): void {
    this.value.set('');
    this.field().nativeElement.value = '';
  }

  protected onInput(el: HTMLInputElement): void {
    const digits = el.value.replace(/\D/g, '').slice(0, 6);
    el.value = digits;
    this.value.set(digits);
    if (digits.length === 6) this.completed.emit(digits);
  }

  protected boxClass(i: number): string {
    if (this.invalid()) return 'border-rose-400 dark:border-rose-500';
    if (this.focused() && i === Math.min(this.value().length, 5)) {
      return 'border-brand-500 ring-4 ring-brand-500/15 dark:border-brand-400';
    }
    return this.value()[i] ? 'border-slate-300 dark:border-slate-600' : 'border-slate-200 dark:border-slate-700';
  }
}
