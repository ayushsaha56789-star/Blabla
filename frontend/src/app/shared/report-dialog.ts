import { ChangeDetectionStrategy, Component, computed, ElementRef, inject, input, OnInit, output, signal, viewChild } from '@angular/core';
import { problemOf } from '../core/errors';
import { ModerationApi, REASONS, REASONS_FOR, ReportReason, ReportTarget } from '../core/moderation-api';
import { ToastService } from '../core/toast.service';
import { Icon } from './icon';

const TITLES: Record<ReportTarget, string> = {
  MEMBER: 'Report {name}',
  RIDE: 'Report this ride',
  MESSAGE: 'Report this message',
  REVIEW: 'Report this review',
};

/** Tell SaathChalo about someone or something that breaks the rules. Staff read every report within 24 hours. */
@Component({
  selector: 'app-report-dialog',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [Icon],
  host: { '(document:keydown.escape)': 'closed.emit()' },
  template: `
    <div class="fixed inset-0 z-50 flex items-end justify-center bg-slate-950/50 p-0 backdrop-blur-sm sm:items-center sm:p-4" (click)="closed.emit()">
      <form #panel class="card max-h-[92dvh] w-full max-w-md animate-pop overflow-y-auto rounded-b-none p-6 sm:rounded-b-3xl" role="dialog" aria-modal="true"
            aria-labelledby="report-title" (click)="$event.stopPropagation()" (submit)="send($event)" tabindex="-1">
        <div class="flex items-start justify-between gap-3">
          <h2 id="report-title" class="text-lg font-bold">{{ title() }}</h2>
          <button type="button" class="btn btn-ghost btn-icon -mt-1 -mr-2" (click)="closed.emit()" aria-label="Close"><app-icon name="close" [size]="18" /></button>
        </div>
        <p class="mt-1 text-sm text-slate-600 dark:text-slate-400">What's wrong? {{ name() ? name() + " won't know who reported them." : "Your report is private." }}</p>
        <fieldset class="mt-4 space-y-2">
          <legend class="sr-only">Reason</legend>
          @for (r of reasons(); track r) {
            <label class="flex cursor-pointer items-center gap-3 rounded-2xl border px-4 py-3 transition"
                   [class]="reason() === r ? 'border-brand-500 bg-brand-50 dark:border-brand-400 dark:bg-brand-500/10' : 'border-slate-200 hover:border-slate-300 dark:border-slate-700'">
              <input type="radio" name="reason" class="accent-brand-600" [value]="r" [checked]="reason() === r" (change)="reason.set(r)" />
              <span class="text-sm font-medium">{{ labels[r] }}</span>
            </label>
          }
        </fieldset>
        <label for="report-details" class="label mt-4">Tell us more <span class="font-normal text-slate-500">(optional)</span></label>
        <textarea id="report-details" class="input min-h-20 text-sm" maxlength="1000" [value]="details()" (input)="details.set($any($event.target).value)"
                  placeholder="What happened, and when?"></textarea>
        @if (error()) { <p class="field-error" role="alert">{{ error() }}</p> }
        <p class="mt-3 text-xs text-slate-500 dark:text-slate-400">In danger right now? Call <a href="tel:112" class="font-semibold underline">112</a>.</p>
        <div class="mt-5 flex justify-end gap-2">
          <button type="button" class="btn btn-ghost" (click)="closed.emit()">Cancel</button>
          <button type="submit" class="btn btn-primary" [disabled]="!reason() || sending()">{{ sending() ? 'Sending…' : 'Send report' }}</button>
        </div>
      </form>
    </div>
  `,
})
export class ReportDialog implements OnInit {
  private readonly api = inject(ModerationApi);
  private readonly toast = inject(ToastService);

  readonly targetType = input.required<ReportTarget>();
  readonly targetId = input.required<string>();
  /** The member concerned, for the wording. */
  readonly name = input<string | null>(null);
  readonly closed = output<void>();

  protected readonly labels = REASONS;
  protected readonly reasons = computed(() => REASONS_FOR[this.targetType()]);
  protected readonly title = computed(() => TITLES[this.targetType()].replace('{name}', this.name() ?? 'this member'));
  protected readonly reason = signal<ReportReason | null>(null);
  protected readonly details = signal('');
  protected readonly sending = signal(false);
  protected readonly error = signal<string | null>(null);

  private readonly panel = viewChild.required<ElementRef<HTMLElement>>('panel');

  ngOnInit(): void {
    queueMicrotask(() => this.panel().nativeElement.focus());
  }

  protected async send(event: Event): Promise<void> {
    event.preventDefault();
    const reason = this.reason();
    if (!reason) return;
    this.sending.set(true);
    this.error.set(null);
    try {
      await this.api.report(this.targetType(), this.targetId(), reason, this.details().trim() || null);
      this.toast.success('Thanks for telling us. Our team will look into it within 24 hours.');
      this.closed.emit();
    } catch (e) {
      this.error.set(problemOf(e).detail);
    } finally {
      this.sending.set(false);
    }
  }
}
