import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, inject, OnInit, signal } from '@angular/core';
import { problemOf } from '../../core/errors';
import { FeaturesService } from '../../core/features.service';
import { PendingReview, ReviewsApi } from '../../core/reviews-api';
import { ToastService } from '../../core/toast.service';
import { Avatar } from '../../shared/avatar';
import { StarInput } from '../../shared/stars';

interface Draft {
  item: PendingReview;
  rating: number;
  comment: string;
  saving: boolean;
  error: string | null;
}

/** "How was your trip?" — reviews this member can still write (up to 14 days after a trip). */
@Component({
  selector: 'app-pending-reviews',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [DatePipe, Avatar, StarInput],
  host: { class: 'block' },
  template: `
    @if (drafts().length) {
      <section class="card mt-6 border-amber-300/70 p-5 sm:p-6 dark:border-amber-500/30" aria-labelledby="rate-heading">
        <h2 id="rate-heading" class="text-lg font-bold">How was your trip?</h2>
        <p class="text-sm text-slate-600 dark:text-slate-400">Reviews help everyone choose who to travel with. You have 14 days after a trip.</p>
        <ul class="mt-4 space-y-4">
          @for (d of drafts(); track d.item.ride.id + d.item.subject.id; let i = $index) {
            <li class="rounded-2xl bg-slate-50 p-4 dark:bg-slate-800/60">
              <div class="flex items-center gap-3">
                <app-avatar [url]="d.item.subject.avatarUrl" [name]="d.item.subject.displayName" [size]="40" />
                <span class="min-w-0">
                  <span class="block font-semibold">{{ d.item.subject.displayName }}</span>
                  <span class="block text-xs text-slate-500 dark:text-slate-400">
                    Your {{ d.item.subjectRole === 'DRIVER' ? 'driver' : 'passenger' }} · {{ d.item.ride.fromName }} → {{ d.item.ride.toName }} · {{ d.item.ride.departureAt | date: 'd MMM' }}
                  </span>
                </span>
              </div>
              <div class="mt-3"><app-star-input [value]="d.rating" (valueChange)="update(i, { rating: $event })" [label]="'Rate ' + d.item.subject.displayName" /></div>
              @if (d.rating) {
                <label class="sr-only" [for]="'review-' + i">What went well, or what could be better? (optional)</label>
                <textarea [id]="'review-' + i" class="input mt-3 min-h-20 text-sm" maxlength="500" [value]="d.comment"
                          (input)="update(i, { comment: $any($event.target).value })" placeholder="What went well, or what could be better? (optional)"></textarea>
                @if (d.error) { <p class="field-error" role="alert">{{ d.error }}</p> }
                <div class="mt-3 flex justify-end">
                  <button type="button" class="btn btn-primary btn-sm" (click)="submit(i)" [disabled]="d.saving">{{ d.saving ? 'Sending…' : 'Post review' }}</button>
                </div>
              }
            </li>
          }
        </ul>
      </section>
    }
  `,
})
export class PendingReviews implements OnInit {
  private readonly api = inject(ReviewsApi);
  private readonly toast = inject(ToastService);
  private readonly features = inject(FeaturesService);

  protected readonly drafts = signal<Draft[]>([]);

  async ngOnInit(): Promise<void> {
    if (!this.features.isOn('reviews')) return;
    try {
      const pending = await this.api.pending();
      this.drafts.set(pending.map((item) => ({ item, rating: 0, comment: '', saving: false, error: null })));
    } catch {
      // Optional prompt; My rides still works.
    }
  }

  protected update(index: number, change: Partial<Draft>): void {
    this.drafts.update((list) => list.map((d, i) => (i === index ? { ...d, ...change } : d)));
  }

  protected async submit(index: number): Promise<void> {
    const d = this.drafts()[index];
    if (!d?.rating || !d.item.subject.id) return;
    this.update(index, { saving: true, error: null });
    try {
      await this.api.write(d.item.ride.id, d.item.subject.id, d.rating, d.comment.trim() || null);
      this.drafts.update((list) => list.filter((_, i) => i !== index));
      this.toast.success(`Thanks! Your review of ${d.item.subject.displayName} is posted.`);
    } catch (e) {
      this.update(index, { saving: false, error: problemOf(e).detail });
    }
  }
}
