import { ChangeDetectionStrategy, Component, inject, OnInit, signal } from '@angular/core';
import { problemOf } from '../../core/errors';
import { SocialApi } from '../../core/social-api';
import { ToastService } from '../../core/toast.service';

/** Interests for vibe match. Others see only a match score, never the list. */
@Component({
  selector: 'app-interests',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'block' },
  template: `
    <section class="card p-5 sm:p-6" aria-labelledby="interests-heading">
      <h2 id="interests-heading" class="text-lg font-bold">Interests</h2>
      <p class="mt-1 text-sm text-slate-600 dark:text-slate-400">Pick up to 8. With your travel preferences they make a "vibe match" score on rides. Others see only the score.</p>
      <div class="mt-3 flex flex-wrap gap-2">
        @for (o of options(); track o) {
          <button type="button" class="chip" [class]="chosen().includes(o) ? '!border-brand-500 !bg-brand-50 !text-brand-800 dark:!bg-brand-500/20 dark:!text-brand-200' : ''"
                  [attr.aria-pressed]="chosen().includes(o)" (click)="toggle(o)">{{ o }}</button>
        }
      </div>
      <div class="mt-4 flex justify-end"><button type="button" class="btn btn-primary btn-sm" (click)="save()" [disabled]="busy() || !dirty()">Save interests</button></div>
    </section>
  `,
})
export class InterestsCard implements OnInit {
  private readonly api = inject(SocialApi);
  private readonly toast = inject(ToastService);

  protected readonly options = signal<string[]>([]);
  protected readonly chosen = signal<string[]>([]);
  protected readonly dirty = signal(false);
  protected readonly busy = signal(false);

  async ngOnInit(): Promise<void> {
    try {
      const v = await this.api.vibe();
      this.options.set(v.options);
      this.chosen.set(v.interests);
    } catch {
      // The rest of the profile still works.
    }
  }

  protected toggle(o: string): void {
    const list = this.chosen();
    if (list.includes(o)) this.chosen.set(list.filter((x) => x !== o));
    else if (list.length < 8) this.chosen.set([...list, o]);
    else this.toast.info('Up to 8 interests.');
    this.dirty.set(true);
  }

  protected async save(): Promise<void> {
    this.busy.set(true);
    try {
      this.chosen.set((await this.api.saveInterests(this.chosen())).interests);
      this.dirty.set(false);
      this.toast.success('Interests saved.');
    } catch (e) {
      this.toast.error(problemOf(e).detail);
    } finally {
      this.busy.set(false);
    }
  }
}
