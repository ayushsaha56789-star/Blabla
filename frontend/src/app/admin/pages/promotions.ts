import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, inject, OnInit, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { problemOf } from '../../core/errors';
import { AdminPromotionRow, clickRate, promoStatus, reach } from '../../core/promotions-api';
import { ToastService } from '../../core/toast.service';
import { Icon } from '../../shared/icon';
import { SponsoredCard } from '../../shared/sponsored-card';
import { AdminApi } from '../admin-api';

type Tab = 'IN_REVIEW' | 'LIVE' | 'ALL';

/** Members' promotions: check them before they go live, and take one down if needed. */
@Component({
  selector: 'app-admin-promotions',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [DatePipe, RouterLink, Icon, SponsoredCard],
  template: `
    <header class="flex flex-wrap items-end justify-between gap-4">
      <div>
        <p class="kicker">Promotions</p>
        <h1 class="mt-1 text-3xl font-extrabold tracking-tight">Promotions</h1>
        <p class="mt-1 text-slate-600 dark:text-slate-400">Check each promotion against the rules (ASCI code; no betting, alcohol, tobacco, adult content, weapons or misleading claims).</p>
      </div>
      <div class="segmented grid-cols-3" role="tablist" aria-label="Show">
        @for (t of tabs; track t.key) {
          <button type="button" role="tab" class="segment px-3 sm:px-4" [class.segment-on]="tab() === t.key" [attr.aria-selected]="tab() === t.key" (click)="show(t.key)">{{ t.label }}</button>
        }
      </div>
    </header>

    @if (rows(); as list) {
      @if (!list.length) {
        <div class="card mt-6 p-10 text-center"><app-icon name="checkCircle" [size]="32" class="text-brand-600" /><p class="mt-3 font-semibold">{{ tab() === 'IN_REVIEW' ? 'Nothing waiting to be checked.' : 'No promotions here.' }}</p></div>
      } @else {
        <ul class="mt-6 space-y-4">
          @for (r of list; track r.promotion.card.id) {
            <li class="card grid grid-cols-1 gap-4 p-5 md:grid-cols-[minmax(0,1fr)_18rem]">
              <div class="min-w-0">
                <p class="flex flex-wrap items-center gap-2">
                  <span class="badge" [class]="'tone-' + status(r).tone">{{ status(r).text }}</span>
                  <a [routerLink]="['/admin/members', r.owner.id]" class="font-semibold hover:underline">{{ r.owner.displayName }}</a>
                  <span class="text-xs text-slate-500">{{ r.promotion.updatedAt | date: 'd MMM, h:mm a' }}</span>
                </p>
                <p class="mt-2 text-sm text-slate-600 dark:text-slate-400">{{ r.promotion.startsOn | date: 'd MMM' }} – {{ r.promotion.endsOn | date: 'd MMM y' }} ·
                  {{ r.promotion.allIndia ? 'All of India' : towns(r) }} · {{ reach(r.promotion.views, r.promotion.clicks) }} ({{ rate(r) }})</p>
                @if (r.promotion.card.linkUrl) { <p class="mt-1 truncate text-sm">Link: <span class="font-mono">{{ r.promotion.card.linkUrl }}</span></p> }
                @if (r.flags) { <p class="mt-2 rounded-xl bg-accent-100/60 p-2 text-sm dark:bg-accent-600/10"><b>Automatic checks noticed:</b> {{ r.flags }}</p> }
                @if (r.promotion.reviewNote) { <p class="mt-2 text-sm"><b>Note:</b> {{ r.promotion.reviewNote }}</p> }
                @if (r.promotion.status === 'IN_REVIEW' || r.promotion.status === 'APPROVED') {
                  <div class="mt-4 flex flex-wrap items-end gap-2 border-t border-slate-100 pt-4 dark:border-slate-800">
                    @if (r.promotion.status === 'IN_REVIEW') {
                      <button type="button" class="btn btn-primary btn-sm" (click)="approve(r)" [disabled]="busy()"><app-icon name="check" [size]="16" /> Approve</button>
                    }
                    <label class="min-w-56 flex-1 text-sm"><span class="label text-xs">{{ r.promotion.status === 'IN_REVIEW' ? 'Or send it back: why?' : 'Take it down: why?' }}</span>
                      <input class="input py-2 text-sm" maxlength="300" [value]="notes()[r.promotion.card.id] ?? ''" (input)="note(r.promotion.card.id, $any($event.target).value)" placeholder="The member will see this" /></label>
                    <button type="button" class="btn btn-ghost btn-sm text-rose-600 dark:text-rose-400" (click)="refuse(r)" [disabled]="busy() || (notes()[r.promotion.card.id] ?? '').trim().length < 3">
                      {{ r.promotion.status === 'IN_REVIEW' ? 'Send back' : 'Take down' }}</button>
                  </div>
                }
              </div>
              <app-sponsored-card [card]="r.promotion.card" [preview]="true" />
            </li>
          }
        </ul>
      }
    } @else { <div class="skeleton mt-6 h-40"></div> }
  `,
})
export class PromotionsAdminPage implements OnInit {
  private readonly api = inject(AdminApi);
  private readonly toast = inject(ToastService);

  protected readonly tabs: { key: Tab; label: string }[] = [
    { key: 'IN_REVIEW', label: 'To check' }, { key: 'LIVE', label: 'Live' }, { key: 'ALL', label: 'All' },
  ];
  protected readonly tab = signal<Tab>('IN_REVIEW');
  protected readonly rows = signal<AdminPromotionRow[] | null>(null);
  protected readonly notes = signal<Record<string, string>>({});
  protected readonly busy = signal(false);
  protected readonly reach = reach;

  ngOnInit(): void {
    void this.load();
  }

  protected status(r: AdminPromotionRow) {
    return promoStatus(r.promotion);
  }

  protected towns(r: AdminPromotionRow): string {
    return r.promotion.cities.map((c) => c.name).join(', ');
  }

  protected rate(r: AdminPromotionRow): string {
    return clickRate(r.promotion.views, r.promotion.clicks);
  }

  protected show(t: Tab): void {
    this.tab.set(t);
    this.rows.set(null);
    void this.load();
  }

  protected note(id: string, value: string): void {
    this.notes.update((n) => ({ ...n, [id]: value }));
  }

  protected approve(r: AdminPromotionRow): Promise<void> {
    return this.act(() => this.api.promotionAction(r.promotion.card.id, 'approve', null), 'Approved: it runs on its dates.');
  }

  protected refuse(r: AdminPromotionRow): Promise<void> {
    const note = (this.notes()[r.promotion.card.id] ?? '').trim();
    const action = r.promotion.status === 'IN_REVIEW' ? 'reject' : 'take-down';
    return this.act(() => this.api.promotionAction(r.promotion.card.id, action, note), action === 'reject' ? 'Sent back.' : 'Taken down.');
  }

  private async act(call: () => Promise<unknown>, done: string): Promise<void> {
    this.busy.set(true);
    try {
      await call();
      this.toast.success(done);
      await this.load();
    } catch (e) {
      this.toast.error(problemOf(e).detail);
    } finally {
      this.busy.set(false);
    }
  }

  private async load(): Promise<void> {
    try {
      this.rows.set(await this.api.promotions(this.tab()));
    } catch (e) {
      this.toast.error(problemOf(e).detail);
      this.rows.set([]);
    }
  }
}
