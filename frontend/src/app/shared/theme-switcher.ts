import { ChangeDetectionStrategy, Component, inject, input } from '@angular/core';
import { ThemeMode, ThemeService } from '../core/theme.service';
import { Icon, IconName } from './icon';

/** Light (white) · Dark (black) · System, as a small segmented control. */
@Component({
  selector: 'app-theme-switcher',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [Icon],
  template: `
    <div class="inline-flex rounded-xl bg-slate-100 p-1 dark:bg-slate-800/80" role="group" aria-label="Theme">
      @for (o of options; track o.mode) {
        <button type="button" (click)="theme.set(o.mode)" [attr.aria-pressed]="theme.mode() === o.mode" [title]="o.label"
                class="flex items-center gap-1.5 rounded-lg px-2.5 py-1.5 text-xs font-semibold text-slate-500 transition
                       hover:text-slate-900 dark:text-slate-400 dark:hover:text-white"
                [class]="theme.mode() === o.mode ? '!bg-white !text-slate-900 shadow-sm dark:!bg-slate-950 dark:!text-white' : ''">
          <app-icon [name]="o.icon" [size]="15" />
          @if (showLabels()) { <span>{{ o.label }}</span> } @else { <span class="sr-only">{{ o.label }}</span> }
        </button>
      }
    </div>
  `,
})
export class ThemeSwitcher {
  protected readonly theme = inject(ThemeService);
  readonly showLabels = input(false);
  protected readonly options: ReadonlyArray<{ mode: ThemeMode; label: string; icon: IconName }> = [
    { mode: 'light', label: 'Light', icon: 'sun' },
    { mode: 'dark', label: 'Dark', icon: 'moon' },
    { mode: 'system', label: 'Auto', icon: 'monitor' },
  ];
}
