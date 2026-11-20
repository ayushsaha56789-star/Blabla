import { ChangeDetectionStrategy, Component, effect, inject, input, signal, untracked } from '@angular/core';
import { problemOf } from '../../core/errors';
import { paise, WalletView } from '../../core/payments-api';
import { ToastService } from '../../core/toast.service';
import { AdminApi } from '../admin-api';
import { ConsoleSessionService } from '../console-session.service';

/** A member's credits and earnings in the console, with goodwill credits for staff who handle refunds. */
@Component({
  selector: 'app-member-wallet',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'block' },
  template: `
    <section class="card p-6" aria-labelledby="wallet-heading">
      <h2 id="wallet-heading" class="text-lg font-bold">Wallet</h2>
      @if (wallet(); as w) {
        <dl class="mt-3 grid grid-cols-3 gap-3 text-sm">
          <div><dt class="text-slate-500 dark:text-slate-400">Credits</dt><dd class="text-lg font-extrabold tabular-nums">{{ money(w.creditsPaise) }}</dd></div>
          <div><dt class="text-slate-500 dark:text-slate-400">Earnings owed</dt><dd class="text-lg font-extrabold tabular-nums">{{ money(w.earningsPaise) }}</dd></div>
          <div><dt class="text-slate-500 dark:text-slate-400">Paid out</dt><dd class="text-lg font-extrabold tabular-nums">{{ money(w.paidOutPaise) }}</dd></div>
        </dl>
        @if (session.can('payments.refund')) {
          <form class="mt-4 grid gap-2 sm:grid-cols-[7rem_1fr_auto] sm:items-end" (submit)="give($event)">
            <label class="text-sm"><span class="label text-xs">Give credits (₹)</span>
              <input class="input py-2 text-sm" type="number" min="1" max="5000" step="1" [value]="rupees()" (input)="rupees.set($any($event.target).value)" /></label>
            <label class="text-sm"><span class="label text-xs">Why (kept in the audit log)</span>
              <input class="input py-2 text-sm" maxlength="200" [value]="reason()" (input)="reason.set($any($event.target).value)" placeholder="Sorry about the cancelled trip" /></label>
            <button type="submit" class="btn btn-secondary btn-sm" [disabled]="busy() || !valid()">Give</button>
          </form>
        }
      } @else if (error()) {
        <p class="mt-2 text-sm text-rose-600 dark:text-rose-400">{{ error() }}</p>
      } @else {
        <div class="skeleton mt-3 h-12"></div>
      }
    </section>
  `,
})
export class MemberWallet {
  private readonly api = inject(AdminApi);
  private readonly toast = inject(ToastService);
  protected readonly session = inject(ConsoleSessionService);

  readonly memberId = input.required<string>();

  protected readonly wallet = signal<WalletView | null>(null);
  protected readonly error = signal<string | null>(null);
  protected readonly busy = signal(false);
  protected readonly rupees = signal('');
  protected readonly reason = signal('');
  protected readonly money = paise;

  constructor() {
    effect(() => {
      const id = this.memberId();
      untracked(() => void this.load(id));
    });
  }

  protected valid(): boolean {
    const r = Number(this.rupees());
    return Number.isInteger(r) && r >= 1 && r <= 5000 && this.reason().trim().length >= 3;
  }

  protected async give(event: Event): Promise<void> {
    event.preventDefault();
    if (!this.valid()) return;
    this.busy.set(true);
    try {
      this.wallet.set(await this.api.grantCredits(this.memberId(), Number(this.rupees()) * 100, this.reason().trim()));
      this.toast.success(`Gave ₹${this.rupees()} in credits.`);
      this.rupees.set('');
      this.reason.set('');
    } catch (e) {
      this.toast.error(problemOf(e).detail);
    } finally {
      this.busy.set(false);
    }
  }

  private async load(id: string): Promise<void> {
    try {
      this.wallet.set(await this.api.memberWallet(id));
    } catch (e) {
      this.error.set(problemOf(e).detail);
    }
  }
}
