import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { ToastService } from '../core/toast.service';
import { Icon } from '../shared/icon';

@Component({
  selector: 'app-toasts',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [Icon],
  host: { class: 'pointer-events-none fixed inset-x-0 bottom-0 z-50 flex flex-col items-center gap-2 p-4 pb-[max(1rem,env(safe-area-inset-bottom))]' },
  template: `
    @for (t of toast.toasts(); track t.id) {
      <div class="pointer-events-auto flex w-full max-w-md animate-fade-up items-start gap-3 rounded-2xl px-4 py-3 text-sm font-medium shadow-2xl"
           [class]="t.kind === 'error' ? 'bg-rose-600 text-white' : 'bg-slate-900 text-white dark:bg-white dark:text-slate-900'"
           [attr.role]="t.kind === 'error' ? 'alert' : 'status'">
        <app-icon [name]="t.kind === 'error' ? 'info' : 'checkCircle'" [size]="20"
                  [class]="t.kind === 'success' ? 'text-brand-400 dark:text-brand-600' : ''" />
        <span class="flex-1">{{ t.text }}</span>
        <button type="button" class="-m-1 rounded-lg p-1 opacity-70 hover:opacity-100" aria-label="Dismiss" (click)="toast.dismiss(t.id)">
          <app-icon name="close" [size]="16" />
        </button>
      </div>
    }
  `,
})
export class Toasts {
  protected readonly toast = inject(ToastService);
}
