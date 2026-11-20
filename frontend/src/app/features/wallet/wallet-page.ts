import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, inject, OnInit, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { problemOf } from '../../core/errors';
import { paise, PaymentsApi, walletLineLabel, WalletView } from '../../core/payments-api';
import { Icon } from '../../shared/icon';

/** SaathChalo credits, and for drivers: what they earned and what was paid out. */
@Component({
  selector: 'app-wallet-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [DatePipe, RouterLink, Icon],
  template: `
    <div class="container-page max-w-3xl py-8 sm:py-12">
      <header>
        <p class="kicker">Your money on SaathChalo</p>
        <h1 class="mt-1 text-3xl font-extrabold tracking-tight sm:text-4xl">Wallet</h1>
      </header>

      @if (error()) {
        <p class="card mt-6 p-6 text-center" role="alert">{{ error() }}</p>
      } @else if (wallet(); as w) {
        <div class="mt-6 grid gap-4 sm:grid-cols-2">
          <section class="card p-6" aria-labelledby="credits-heading">
            <div class="flex items-center gap-3">
              <span class="flex h-11 w-11 items-center justify-center rounded-2xl bg-brand-50 text-brand-700 dark:bg-brand-500/15 dark:text-brand-300"><app-icon name="sparkles" [size]="22" /></span>
              <h2 id="credits-heading" class="font-bold">SaathChalo credits</h2>
            </div>
            <p class="mt-4 text-4xl font-extrabold tracking-tight tabular-nums">{{ money(w.creditsPaise) }}</p>
            <p class="mt-2 text-sm text-slate-600 dark:text-slate-400">Use them when you pay for a seat online. Credits can't be withdrawn or given to someone else.</p>
          </section>
          <section class="card p-6" aria-labelledby="earnings-heading">
            <div class="flex items-center gap-3">
              <span class="flex h-11 w-11 items-center justify-center rounded-2xl bg-accent-100 text-accent-600 dark:bg-accent-600/15 dark:text-accent-300"><app-icon name="rupee" [size]="22" /></span>
              <h2 id="earnings-heading" class="font-bold">Ride earnings</h2>
            </div>
            <p class="mt-4 text-4xl font-extrabold tracking-tight tabular-nums">{{ money(w.earningsPaise) }}</p>
            <p class="mt-2 text-sm text-slate-600 dark:text-slate-400">
              Seats paid online reach you after each trip. We send your payout as a link to your phone, where you choose your own UPI ID or bank account.
              @if (w.paidOutPaise) { <span class="block mt-1 font-medium text-slate-800 dark:text-slate-200">Paid out so far: {{ money(w.paidOutPaise) }}</span> }
            </p>
          </section>
        </div>

        @if (w.payouts.length) {
          <section class="mt-8" aria-labelledby="payouts-heading">
            <h2 id="payouts-heading" class="kicker mb-3">Payouts</h2>
            <ul class="card divide-y divide-slate-100 dark:divide-slate-800">
              @for (p of w.payouts; track p.id) {
                <li class="flex flex-wrap items-center gap-3 p-4">
                  <span class="min-w-0 flex-1">
                    <span class="block font-semibold">{{ money(p.amountPaise) }}</span>
                    <span class="block text-xs text-slate-500 dark:text-slate-400">{{ p.createdAt | date: 'd MMM y' }} · {{ p.method === 'LINK' ? 'Payout link' : 'Sent to you directly' }}</span>
                  </span>
                  @if (p.status === 'SENT' && p.linkUrl) { <a [href]="p.linkUrl" target="_blank" rel="noopener noreferrer" class="btn btn-primary btn-sm">Claim payout</a> }
                  <span class="badge" [class]="p.status === 'PAID' ? 'badge-success' : p.status === 'SENT' ? 'badge-warning' : 'badge-muted'">
                    {{ p.status === 'PAID' ? 'Paid' : p.status === 'SENT' ? 'Waiting for you' : 'Cancelled' }}
                  </span>
                </li>
              }
            </ul>
          </section>
        }

        <section class="mt-8" aria-labelledby="history-heading">
          <h2 id="history-heading" class="kicker mb-3">History</h2>
          @if (!w.history.length) {
            <div class="card p-8 text-center text-slate-600 dark:text-slate-400">
              <app-icon name="history" [size]="28" class="text-slate-400" />
              <p class="mt-2">Nothing here yet. Credits and earnings show up here.</p>
              <a routerLink="/search" class="btn btn-secondary btn-sm mt-4">Find a ride</a>
            </div>
          } @else {
            <ul class="card divide-y divide-slate-100 dark:divide-slate-800">
              @for (l of w.history; track $index) {
                <li class="flex items-center gap-3 p-4">
                  <span class="min-w-0 flex-1">
                    <span class="block font-medium">{{ label(l) }}</span>
                    <span class="block text-xs text-slate-500 dark:text-slate-400">{{ l.at | date: 'd MMM y, h:mm a' }} · {{ l.account === 'CREDITS' ? 'Credits' : 'Earnings' }}</span>
                  </span>
                  <span class="font-semibold tabular-nums" [class]="l.amountPaise >= 0 ? 'text-brand-700 dark:text-brand-400' : ''">{{ l.amountPaise >= 0 ? '+' : '' }}{{ money(l.amountPaise) }}</span>
                </li>
              }
            </ul>
          }
        </section>
      } @else {
        <div class="mt-6 grid gap-4 sm:grid-cols-2" aria-busy="true"><div class="skeleton h-44"></div><div class="skeleton h-44"></div></div>
      }
    </div>
  `,
})
export class WalletPage implements OnInit {
  private readonly api = inject(PaymentsApi);
  protected readonly wallet = signal<WalletView | null>(null);
  protected readonly error = signal<string | null>(null);
  protected readonly money = paise;
  protected readonly label = walletLineLabel;

  async ngOnInit(): Promise<void> {
    try {
      this.wallet.set(await this.api.wallet());
    } catch (e) {
      this.error.set(problemOf(e).detail);
    }
  }
}
