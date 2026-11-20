import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { CheckoutService } from '../core/checkout.service';
import { problemOf } from '../core/errors';
import { paise, PaymentsApi } from '../core/payments-api';
import { Icon } from './icon';

/** Development only: stands in for Razorpay's checkout. No money moves. */
@Component({
  selector: 'app-pretend-checkout',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [DatePipe, Icon],
  template: `
    @if (checkout.pretend(); as p) {
      <div class="fixed inset-0 z-50 flex items-end justify-center bg-slate-950/60 p-0 backdrop-blur-sm sm:items-center sm:p-4" role="presentation">
        <section class="card w-full max-w-sm animate-pop rounded-b-none p-6 sm:rounded-b-3xl" role="dialog" aria-modal="true" aria-labelledby="pretend-title">
          <p class="badge badge-warning"><app-icon name="info" [size]="14" /> Test payment, no real money</p>
          <h2 id="pretend-title" class="mt-3 text-xl font-bold">Pay {{ money(p.view.onlinePaise) }}</h2>
          <p class="mt-1 text-sm text-slate-600 dark:text-slate-400">{{ p.view.description }}</p>
          @if (p.view.creditsPaise) { <p class="mt-2 text-sm">{{ money(p.view.creditsPaise) }} comes from your SaathChalo credits.</p> }
          <p class="mt-2 text-xs text-slate-500 dark:text-slate-400">Seat held until {{ p.view.holdUntil | date: 'h:mm a' }}. In the real app this is Razorpay's UPI and card window.</p>
          @if (error()) { <p class="field-error" role="alert">{{ error() }}</p> }
          <div class="mt-5 grid grid-cols-2 gap-2">
            <button type="button" class="btn btn-ghost" (click)="close()" [disabled]="busy()">Not now</button>
            <button type="button" class="btn btn-primary" (click)="payNow()" [disabled]="busy()">{{ busy() ? 'Paying…' : 'Pay' }}</button>
          </div>
        </section>
      </div>
    }
  `,
})
export class PretendCheckout {
  protected readonly checkout = inject(CheckoutService);
  private readonly api = inject(PaymentsApi);
  protected readonly busy = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly money = paise;

  protected async payNow(): Promise<void> {
    const p = this.checkout.pretend();
    if (!p) return;
    this.busy.set(true);
    this.error.set(null);
    try {
      p.done(await this.api.pretendPay(p.view.paymentId));
    } catch (e) {
      this.error.set(problemOf(e).detail);
    } finally {
      this.busy.set(false);
    }
  }

  protected close(): void {
    this.checkout.pretend()?.done(null);
  }
}
