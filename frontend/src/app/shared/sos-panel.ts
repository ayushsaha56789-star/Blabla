import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, inject, input, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { problemOf } from '../core/errors';
import { currentFix, SafetyApi, SosResult } from '../core/safety-api';
import { ToastService } from '../core/toast.service';
import { Icon } from './icon';

/**
 * The SOS button for a trip that isn't a ride (a hired driver, say): call 112 first, then alert SaathChalo staff and
 * the member's emergency contacts with the phone's position.
 */
@Component({
  selector: 'app-sos-panel',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [DatePipe, RouterLink, Icon],
  host: { class: 'block' },
  template: `
    @switch (state()) {
      @case ('idle') {
        <button type="button" class="btn w-full bg-rose-600 py-4 text-base text-white shadow-lg shadow-rose-600/25 hover:bg-rose-700" (click)="state.set('confirm')">
          <app-icon name="shieldAlert" [size]="22" /> SOS — I need help
        </button>
      }
      @case ('confirm') {
        <div class="rounded-2xl border-2 border-rose-500 p-4" role="alertdialog" aria-labelledby="sos-panel-title">
          <p id="sos-panel-title" class="font-bold text-rose-700 dark:text-rose-300">Get help now</p>
          <p class="mt-1 text-sm text-slate-600 dark:text-slate-400">In danger? Call the police first. Then let SaathChalo and your emergency contacts know.</p>
          <div class="mt-3 grid gap-2 sm:grid-cols-2">
            <a href="tel:112" class="btn bg-rose-600 py-3.5 text-white hover:bg-rose-700"><app-icon name="phone" [size]="18" /> Call 112</a>
            <button type="button" class="btn btn-secondary py-3.5" (click)="raise()" [disabled]="busy()">{{ busy() ? 'Sending alert…' : 'Alert SaathChalo & my contacts' }}</button>
          </div>
          <button type="button" class="btn btn-ghost btn-sm mt-2" (click)="state.set('idle')">I'm okay, go back</button>
        </div>
      }
      @case ('sent') {
        <div class="rounded-2xl bg-rose-50 p-4 text-sm text-rose-900 dark:bg-rose-500/10 dark:text-rose-100" role="status">
          <p class="font-bold">Alert sent at {{ result()?.createdAt | date: 'h:mm a' }}</p>
          <p class="mt-1">SaathChalo support can see your alert and where you are.
            @if (result()?.contactsNotified) { We texted {{ result()!.contactsNotified }} emergency {{ result()!.contactsNotified === 1 ? 'contact' : 'contacts' }}. }
            @else { You have no emergency contacts yet, so nobody was texted. <a routerLink="/profile" fragment="safety" class="font-semibold underline">Add them</a>. }
          </p>
          <a href="tel:112" class="btn mt-3 w-full bg-rose-600 py-3 text-white hover:bg-rose-700"><app-icon name="phone" [size]="18" /> Call 112</a>
        </div>
      }
    }
  `,
})
export class SosPanel {
  private readonly api = inject(SafetyApi);
  private readonly toast = inject(ToastService);

  /** The ride this is about, if any. */
  readonly rideId = input<string | null>(null);

  protected readonly state = signal<'idle' | 'confirm' | 'sent'>('idle');
  protected readonly busy = signal(false);
  protected readonly result = signal<SosResult | null>(null);

  protected async raise(): Promise<void> {
    this.busy.set(true);
    try {
      this.result.set(await this.api.sos(this.rideId(), await currentFix(4000)));
      this.state.set('sent');
    } catch (e) {
      this.toast.error(problemOf(e).detail + ' Please call 112.');
    } finally {
      this.busy.set(false);
    }
  }
}
