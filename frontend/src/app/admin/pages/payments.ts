import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, inject, OnInit, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { problemOf } from '../../core/errors';
import { paise, PayoutView } from '../../core/payments-api';
import { ToastService } from '../../core/toast.service';
import { Avatar } from '../../shared/avatar';
import { Icon } from '../../shared/icon';
import { AdminApi } from '../admin-api';
import { AdminPaymentRow, OwedRow, PaymentsSummary } from '../admin.models';
import { ConsoleSessionService } from '../console-session.service';

type Tab = 'all' | 'problems' | 'payouts';

/** Money: what's held for trips, owed to drivers and given as credits, the ledger's health, and payouts. */
@Component({
  selector: 'app-admin-payments',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [DatePipe, RouterLink, Avatar, Icon],
  template: `
    <header class="flex flex-wrap items-end justify-between gap-4">
      <div>
        <p class="kicker">Money</p>
        <h1 class="mt-1 text-3xl font-extrabold tracking-tight">Payments</h1>
        <p class="mt-1 text-slate-600 dark:text-slate-400">Seats paid online, refunds, SaathChalo credits and driver payouts.</p>
      </div>
      <div class="segmented grid-cols-3" role="tablist" aria-label="Show">
        @for (t of tabs; track t.key) {
          <button type="button" role="tab" class="segment px-3 sm:px-4" [class.segment-on]="tab() === t.key" [attr.aria-selected]="tab() === t.key" (click)="show(t.key)">{{ t.label }}</button>
        }
      </div>
    </header>

    @if (summary(); as s) {
      @if (!s.onlineReady) {
        <p class="mt-6 flex items-start gap-3 rounded-2xl bg-accent-100/70 p-4 text-sm dark:bg-accent-600/10" role="note">
          <app-icon name="info" class="shrink-0 text-accent-600" />
          <span>Online payments aren't set up: add the Razorpay keys (<code>RAZORPAY_KEY_ID</code>, <code>RAZORPAY_KEY_SECRET</code>, <code>RAZORPAY_WEBHOOK_SECRET</code>) and set <code>PAYMENTS_PROVIDER=razorpay</code>. Until then members pay drivers directly.</span>
        </p>
      } @else if (s.gateway === 'FAKE') {
        <p class="mt-6 rounded-2xl bg-slate-100 p-3 text-sm dark:bg-slate-800/60">Test mode: payments use a pretend gateway and no real money moves.</p>
      }
      <div class="mt-6 grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
        @for (t of tiles(s); track t.label) {
          <div class="card p-5">
            <p class="text-sm text-slate-500 dark:text-slate-400">{{ t.label }}</p>
            <p class="mt-1 text-2xl font-extrabold tracking-tight tabular-nums">{{ t.value }}</p>
          </div>
        }
      </div>
      <div class="mt-3 flex flex-wrap gap-2 text-sm">
        @for (c of checks(s); track c.label) {
          <span class="badge" [class]="c.ok ? 'badge-success' : 'badge-danger'"><app-icon [name]="c.ok ? 'checkCircle' : 'shieldAlert'" [size]="14" /> {{ c.label }}</span>
        }
        @if (s.refundsFailed) { <span class="badge badge-danger">{{ s.refundsFailed }} failed {{ s.refundsFailed === 1 ? 'refund' : 'refunds' }}</span> }
        @if (s.refundsPending) { <span class="badge badge-warning">{{ s.refundsPending }} {{ s.refundsPending === 1 ? 'refund' : 'refunds' }} on the way</span> }
      </div>
    } @else if (!error()) {
      <div class="skeleton mt-6 h-28"></div>
    }

    @if (error()) { <p class="field-error mt-4" role="alert">{{ error() }}</p> }

    @if (tab() === 'payouts') {
      <section class="mt-8" aria-labelledby="owed-heading">
        <h2 id="owed-heading" class="kicker mb-3">Owed to drivers</h2>
        @if (owed(); as list) {
          @if (!list.length) {
            <div class="card p-8 text-center text-slate-600 dark:text-slate-400">Nobody is owed anything right now.</div>
          } @else {
            <ul class="space-y-3">
              @for (o of list; track o.driver.id) {
                <li class="card p-4">
                  <div class="flex flex-wrap items-center gap-3">
                    <a [routerLink]="['/admin/members', o.driver.id]" class="flex min-w-0 flex-1 items-center gap-3">
                      <app-avatar [url]="o.driver.avatarUrl" [name]="o.driver.displayName" [size]="40" />
                      <span class="min-w-0"><span class="block truncate font-semibold">{{ o.driver.displayName }}</span>
                        <span class="block text-xs text-slate-500 dark:text-slate-400">@if (o.lastPayoutAt) { Last payout {{ o.lastPayoutAt | date: 'd MMM' }} } @else { No payouts yet }</span></span>
                    </a>
                    <span class="text-lg font-extrabold tabular-nums">{{ money(o.earningsPaise) }}</span>
                    @if (session.can('payouts.approve')) {
                      <button type="button" class="btn btn-primary btn-sm" (click)="startPayout(o)">Pay out</button>
                    }
                  </div>
                  @if (paying() === o.driver.id) {
                    <div class="mt-4 space-y-3 rounded-2xl bg-slate-50 p-4 dark:bg-slate-800/50">
                      <div class="segmented grid-cols-2" role="radiogroup" aria-label="How">
                        <button type="button" role="radio" class="segment" [class.segment-on]="method() === 'LINK'" [attr.aria-checked]="method() === 'LINK'" (click)="method.set('LINK')">Payout link</button>
                        <button type="button" role="radio" class="segment" [class.segment-on]="method() === 'MANUAL'" [attr.aria-checked]="method() === 'MANUAL'" (click)="method.set('MANUAL')">Paid by hand</button>
                      </div>
                      <p class="text-xs text-slate-500 dark:text-slate-400">
                        @if (method() === 'LINK') { Razorpay texts the driver a link; they choose their own UPI ID or bank account. Needs RazorpayX. }
                        @else { You already sent the money (UPI or bank). Record its reference so it can be traced. }
                      </p>
                      <input class="input text-sm" maxlength="120" [placeholder]="method() === 'MANUAL' ? 'UPI or bank reference (required)' : 'Note (optional)'"
                             [value]="reference()" (input)="reference.set($any($event.target).value)" />
                      <div class="flex justify-end gap-2">
                        <button type="button" class="btn btn-ghost btn-sm" (click)="paying.set(null)">Cancel</button>
                        <button type="button" class="btn btn-primary btn-sm" (click)="payout(o)" [disabled]="busy() || (method() === 'MANUAL' && !reference().trim())">Pay {{ money(o.earningsPaise) }}</button>
                      </div>
                    </div>
                  }
                </li>
              }
            </ul>
          }
        } @else {
          <div class="skeleton h-24"></div>
        }
      </section>
      @if (payouts().length) {
        <section class="mt-8" aria-labelledby="payouts-heading">
          <h2 id="payouts-heading" class="kicker mb-3">Recent payouts</h2>
          <ul class="card divide-y divide-slate-100 dark:divide-slate-800">
            @for (p of payouts(); track p.id) {
              <li class="flex flex-wrap items-center gap-3 p-4 text-sm">
                <span class="min-w-0 flex-1"><span class="font-semibold">{{ p.driver.displayName }}</span>
                  <span class="block text-xs text-slate-500 dark:text-slate-400">{{ p.createdAt | date: 'd MMM y, h:mm a' }} · {{ p.method === 'LINK' ? 'Payout link' : 'By hand' }}@if (p.reference) { · {{ p.reference }} }</span></span>
                <span class="font-semibold tabular-nums">{{ money(p.amountPaise) }}</span>
                <span class="badge" [class]="p.status === 'PAID' ? 'badge-success' : p.status === 'SENT' ? 'badge-warning' : 'badge-muted'">{{ p.status === 'PAID' ? 'Paid' : p.status === 'SENT' ? 'Link sent' : 'Cancelled' }}</span>
              </li>
            }
          </ul>
        </section>
      }
    } @else {
      @if (rows(); as list) {
        @if (!list.length) {
          <div class="card mt-8 p-10 text-center">
            <app-icon name="checkCircle" [size]="32" class="text-brand-600" />
            <p class="mt-3 font-semibold">{{ tab() === 'problems' ? 'No problems. Every refund went through.' : 'No online payments yet.' }}</p>
          </div>
        } @else {
          <ul class="mt-8 space-y-3">
            @for (p of list; track p.id) {
              <li class="card p-4 text-sm">
                <div class="flex flex-wrap items-center gap-x-3 gap-y-1">
                  <span class="font-semibold">{{ p.payer.displayName }}</span>
                  <app-icon name="arrowRight" [size]="14" class="text-slate-400" />
                  <span class="font-semibold">{{ p.driver.displayName }}</span>
                  <span class="text-slate-500 dark:text-slate-400">{{ p.route }}@if (p.departureAt) { · {{ p.departureAt | date: 'd MMM, h:mm a' }} }</span>
                  <span class="ml-auto font-bold tabular-nums">{{ money(p.creditsPaise + p.onlinePaise) }}</span>
                  <span class="badge" [class]="'tone-' + state(p).tone">{{ state(p).text }}</span>
                </div>
                <p class="mt-1 text-xs text-slate-500 dark:text-slate-400">
                  {{ p.createdAt | date: 'd MMM, h:mm a' }} · {{ p.gateway === 'CREDITS' ? 'credits only' : money(p.onlinePaise) + ' online' }}@if (p.creditsPaise && p.gateway !== 'CREDITS') { + {{ money(p.creditsPaise) }} credits }@if (p.feePaise) { · fee {{ money(p.feePaise) }} }
                </p>
                @for (r of p.refunds; track r.id) {
                  <div class="mt-2 flex flex-wrap items-center gap-2 rounded-xl bg-slate-50 px-3 py-2 dark:bg-slate-800/50">
                    <span>Refund {{ money(r.onlinePaise + r.creditsPaise) }}@if (r.creditsPaise) { ({{ money(r.creditsPaise) }} as credits) }</span>
                    <span class="text-xs text-slate-500 dark:text-slate-400">{{ r.reason }}</span>
                    <span class="badge ml-auto" [class]="r.status === 'PROCESSED' ? 'badge-success' : r.status === 'FAILED' ? 'badge-danger' : 'badge-warning'">{{ r.status === 'PROCESSED' ? 'Done' : r.status === 'FAILED' ? 'Failed' : 'On the way' }}</span>
                    @if (r.status === 'FAILED') { <button type="button" class="btn btn-secondary btn-sm" (click)="retry(r.id)" [disabled]="busy()">Try again</button> }
                  </div>
                }
              </li>
            }
          </ul>
        }
      } @else if (!error()) {
        <div class="skeleton mt-8 h-40"></div>
      }
    }
  `,
})
export class PaymentsPage implements OnInit {
  private readonly api = inject(AdminApi);
  private readonly toast = inject(ToastService);
  protected readonly session = inject(ConsoleSessionService);

  protected readonly tabs: { key: Tab; label: string }[] = [
    { key: 'all', label: 'Payments' },
    { key: 'problems', label: 'Problems' },
    { key: 'payouts', label: 'Payouts' },
  ];
  protected readonly tab = signal<Tab>('all');
  protected readonly summary = signal<PaymentsSummary | null>(null);
  protected readonly rows = signal<AdminPaymentRow[] | null>(null);
  protected readonly owed = signal<OwedRow[] | null>(null);
  protected readonly payouts = signal<PayoutView[]>([]);
  protected readonly error = signal<string | null>(null);
  protected readonly busy = signal(false);
  protected readonly paying = signal<string | null>(null);
  protected readonly method = signal<'LINK' | 'MANUAL'>('MANUAL');
  protected readonly reference = signal('');
  protected readonly money = paise;

  ngOnInit(): void {
    void this.load();
  }

  protected show(tab: Tab): void {
    this.tab.set(tab);
    this.rows.set(null);
    void this.load();
  }

  protected tiles(s: PaymentsSummary): { label: string; value: string }[] {
    return [
      { label: 'Held for upcoming trips', value: paise(s.heldForTripsPaise) },
      { label: 'Owed to drivers', value: paise(s.owedToDriversPaise) },
      { label: 'Members’ credits', value: paise(s.creditsOutstandingPaise) },
      { label: 'Service fees earned', value: paise(s.feesEarnedPaise) },
    ];
  }

  protected checks(s: PaymentsSummary): { label: string; ok: boolean }[] {
    return [
      { label: 'Ledger balanced', ok: s.ledgerBalanced },
      { label: 'Wallets match the ledger', ok: s.walletsMatch },
      { label: 'Held money matches open payments', ok: s.escrowMatches },
    ];
  }

  protected state(p: AdminPaymentRow): { text: string; tone: string } {
    if (p.status === 'CREATED') return { text: 'Waiting for payment', tone: 'warning' };
    if (p.status === 'EXPIRED') return { text: 'Not paid', tone: 'muted' };
    switch (p.outcome) {
      case 'SETTLED': return { text: 'Paid to driver', tone: 'success' };
      case 'REFUNDED': return { text: 'Refunded', tone: 'muted' };
      case 'SPLIT': return { text: 'Partly refunded', tone: 'muted' };
      default: return { text: 'Held for the trip', tone: 'success' };
    }
  }

  protected startPayout(o: OwedRow): void {
    this.paying.set(o.driver.id);
    this.reference.set('');
    this.method.set(this.summary()?.onlineReady ? 'LINK' : 'MANUAL');
  }

  protected async payout(o: OwedRow): Promise<void> {
    if (!o.driver.id) return;
    this.busy.set(true);
    try {
      const p = await this.api.payout(o.driver.id, this.method(), this.reference().trim() || null);
      this.toast.success(p.status === 'PAID' ? `${paise(p.amountPaise)} recorded as paid to ${o.driver.displayName}.` : `Payout link sent to ${o.driver.displayName}.`);
      this.paying.set(null);
      await this.load();
    } catch (e) {
      this.toast.error(problemOf(e).detail);
    } finally {
      this.busy.set(false);
    }
  }

  protected async retry(refundId: string): Promise<void> {
    this.busy.set(true);
    try {
      await this.api.retryRefund(refundId);
      this.toast.success("We'll try that refund again within a minute.");
      await this.load();
    } catch (e) {
      this.toast.error(problemOf(e).detail);
    } finally {
      this.busy.set(false);
    }
  }

  private async load(): Promise<void> {
    try {
      this.summary.set(await this.api.paymentsSummary());
      if (this.tab() === 'payouts') {
        const [owed, payouts] = await Promise.all([this.api.payoutsOwed(), this.api.payouts()]);
        this.owed.set(owed);
        this.payouts.set(payouts);
      } else {
        this.rows.set(await this.api.payments(this.tab() === 'problems' ? 'problems' : 'all'));
      }
      this.error.set(null);
    } catch (e) {
      this.error.set(problemOf(e).detail);
    }
  }
}
