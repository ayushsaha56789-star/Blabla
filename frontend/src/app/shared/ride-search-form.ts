import { ChangeDetectionStrategy, Component, effect, inject, input, signal, untracked } from '@angular/core';
import { Router } from '@angular/router';
import { Place } from '../core/rides.models';
import { todayIso } from '../core/util';
import { Icon } from './icon';
import { PlaceInput } from './place-input';

/** "From → To · Date · Seats" search bar used on the home page and the search page. */
@Component({
  selector: 'app-ride-search-form',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [Icon, PlaceInput],
  host: { class: 'block' },
  template: `
    <form (submit)="search($event)" novalidate class="card p-2 shadow-xl shadow-slate-900/5 sm:p-2.5" aria-label="Search rides">
      <div class="flex flex-col gap-1 lg:flex-row lg:items-stretch">
        <div class="relative flex flex-col gap-1 lg:flex-[2] lg:flex-row">
          <label class="field-box lg:flex-1">
            <app-icon name="pin" class="text-brand-600 dark:text-brand-400" />
            <span class="min-w-0 flex-1">
              <span class="field-caption">Leaving from</span>
              <app-place-input [(value)]="from" inputClass="field-input" placeholder="City or town" ariaLabel="Leaving from"
                               [allowLocation]="true" [exclude]="to() ? [to()!.id] : []" (picked)="error.set(null)" />
            </span>
          </label>
          <button type="button" (click)="swap()" aria-label="Swap from and to"
                  class="absolute top-1/2 right-3 z-10 -translate-y-1/2 rounded-full border border-slate-200 bg-white p-2 text-slate-500 shadow-sm transition hover:rotate-180 hover:text-brand-600
                         lg:static lg:translate-y-0 lg:self-center dark:border-slate-700 dark:bg-slate-900">
            <app-icon name="swap" [size]="16" class="lg:rotate-90" />
          </button>
          <label class="field-box lg:flex-1">
            <app-icon name="pin" class="text-accent-500" />
            <span class="min-w-0 flex-1">
              <span class="field-caption">Going to</span>
              <app-place-input [(value)]="to" inputClass="field-input" placeholder="City or town" ariaLabel="Going to"
                               [exclude]="from() ? [from()!.id] : []" (picked)="error.set(null)" />
            </span>
          </label>
        </div>
        <div class="grid grid-cols-2 gap-1 lg:flex lg:flex-[1.3]">
          <label class="field-box max-sm:px-3 lg:flex-1">
            <app-icon name="calendar" class="text-slate-400 max-sm:hidden" />
            <span class="min-w-0 flex-1">
              <span class="field-caption">Date</span>
              <input #dateEl type="date" class="field-input" [min]="minDate" [value]="date()" (input)="date.set(dateEl.value)" />
            </span>
          </label>
          <div class="field-box max-sm:px-3 lg:flex-1">
            <app-icon name="users" class="text-slate-400 max-sm:hidden" />
            <span class="min-w-0 flex-1">
              <span class="field-caption" id="seats-label">Passengers</span>
              <span class="flex items-center gap-2" role="group" aria-labelledby="seats-label">
                <button type="button" class="stepper" (click)="seats.set(seats() - 1)" [disabled]="seats() <= 1" aria-label="Fewer passengers">
                  <app-icon name="minus" [size]="14" />
                </button>
                <span class="w-4 text-center font-semibold" aria-live="polite">{{ seats() }}</span>
                <button type="button" class="stepper" (click)="seats.set(seats() + 1)" [disabled]="seats() >= 6" aria-label="More passengers">
                  <app-icon name="plus" [size]="14" />
                </button>
              </span>
            </span>
          </div>
        </div>
        <button type="submit" class="btn btn-primary py-4 text-base lg:px-7">
          <app-icon name="search" [size]="20" /> Search
        </button>
      </div>
      @if (error()) {
        <p class="field-error px-3 pb-1" role="alert">{{ error() }}</p>
      }
    </form>
  `,
  styles: `
    @reference "../../styles.css";
    .field-box {
      @apply flex cursor-text items-center gap-3 rounded-2xl px-4 py-2.5 transition
        hover:bg-slate-50 focus-within:bg-slate-50 dark:hover:bg-slate-800/60 dark:focus-within:bg-slate-800/60;
    }
    .field-caption { @apply block text-xs font-semibold text-slate-500 dark:text-slate-400; }
    .stepper {
      @apply rounded-full border border-slate-200 p-1.5 text-slate-600 transition hover:border-brand-500 hover:text-brand-600
        disabled:opacity-40 dark:border-slate-700 dark:text-slate-300;
    }
  `,
})
export class RideSearchForm {
  private readonly router = inject(Router);

  readonly initialFrom = input<Place | null>(null, { alias: 'from' });
  readonly initialTo = input<Place | null>(null, { alias: 'to' });
  readonly initialDate = input<string | undefined>(undefined, { alias: 'date' });
  readonly initialSeats = input<number | undefined>(undefined, { alias: 'seats' });

  protected readonly minDate = todayIso();
  protected readonly from = signal<Place | null>(null);
  protected readonly to = signal<Place | null>(null);
  protected readonly date = signal(todayIso());
  protected readonly seats = signal(1);
  protected readonly error = signal<string | null>(null);

  constructor() {
    // Follow the page's search (e.g. after the results load, or when going back to an older search).
    effect(() => {
      const from = this.initialFrom();
      const to = this.initialTo();
      const d = this.initialDate();
      const s = this.initialSeats();
      untracked(() => {
        if (from) this.from.set(from);
        if (to) this.to.set(to);
        if (d && d >= this.minDate) this.date.set(d);
        if (s && s >= 1 && s <= 6) this.seats.set(s);
      });
    });
  }

  protected swap(): void {
    const from = this.from();
    this.from.set(this.to());
    this.to.set(from);
  }

  protected search(event: Event): void {
    event.preventDefault();
    const from = this.from();
    const to = this.to();
    if (!from || !to) {
      this.error.set(!from ? 'Choose where you are leaving from (pick a town from the list).' : 'Choose where you are going (pick a town from the list).');
      return;
    }
    if (from.id === to.id) {
      this.error.set('Your start and destination are the same. Pick a different destination.');
      return;
    }
    this.error.set(null);
    void this.router.navigate(['/search'], {
      queryParams: { from: from.name, fromId: from.id, to: to.name, toId: to.id, date: this.date(), seats: this.seats() },
    });
  }
}
