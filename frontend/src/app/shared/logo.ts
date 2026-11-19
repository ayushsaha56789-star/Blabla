import { ChangeDetectionStrategy, Component, input } from '@angular/core';

@Component({
  selector: 'app-logo',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'inline-flex items-center gap-2.5' },
  template: `
    <svg viewBox="0 0 64 64" [attr.width]="size()" [attr.height]="size()" aria-hidden="true">
      <defs>
        <linearGradient id="sc-logo-g" x1="0" y1="0" x2="1" y2="1">
          <stop offset="0" stop-color="#10b981" /><stop offset="1" stop-color="#047857" />
        </linearGradient>
      </defs>
      <rect width="64" height="64" rx="18" fill="url(#sc-logo-g)" />
      <path d="M18 44c8 0 10-24 28-24" fill="none" stroke="#fff" stroke-width="5" stroke-linecap="round" stroke-dasharray="0.1 9" />
      <circle cx="18" cy="44" r="6" fill="#fff" />
      <circle cx="46" cy="20" r="6" fill="#fdba74" />
    </svg>
    @if (withText()) {
      <span class="text-xl font-extrabold tracking-tight">Saath<span class="text-brand-600 dark:text-brand-400">Chalo</span></span>
    }
  `,
})
export class Logo {
  readonly size = input(34);
  readonly withText = input(true);
}
