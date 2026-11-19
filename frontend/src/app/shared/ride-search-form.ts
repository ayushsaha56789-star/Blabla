import { ChangeDetectionStrategy, Component, inject, input, OnInit, signal } from '@angular/core';
import { Router } from '@angular/router';
import { POPULAR_CITIES } from '../core/cities';
import { todayIso } from '../core/util';
import { Icon } from './icon';

/** "From → To · Date · Seats" search bar used on the home page and the search page. */
@Component({
  selector: 'app-ride-search-form',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [Icon],
  host: { class: 'block' },
  template: `
    <form (submit)="search($event)" novalidate class="card p-2 shadow-xl shadow-slate-900/5 sm:p-2.5" aria-label="Search rides">
      <div class="flex flex-col gap-1 lg:flex-row lg:items-stretch">
        <div class="relative flex flex-col gap-1 lg:flex-[2] lg:flex-row">
          <label class="field-box lg:flex-1">
            <app-icon name="pin" class="text-brand-600 dark:text-brand-400" />
            <span class="min-w-0 flex-1">
              <span class="field-caption">Leaving from</span>
              <input #fromEl class="field-input" placeholder="City or place" list="sc-cities" autocomplete="off" required
                     [value]="from()" (input)="from.set(fromEl.value)" />
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
              <input #toEl class="field-input" placeholder="City or place" list="sc-cities" autocomplete="off" required
                     [value]="to()" (input)="to.set(toEl.value)" />
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
      <datalist id="sc-cities">
        @for (city of cities; track city) {
          <option [value]="city"></option>
        }
      </datalist>
    </form>
  `,
  styles: `
    @reference "../../styles.css";
    .field-box {
      @apply flex cursor-text items-center gap-3 rounded-2xl px-4 py-2.5 transition
        hover:bg-slate-50 focus-within:bg-slate-50 dark:hover:bg-slate-800/60 dark:focus-within:bg-slate-800/60;
    }
    .field-caption { @apply block text-xs font-semibold text-slate-500 dark:text-slate-400; }
    .field-input {
      @apply w-full min-w-0 bg-transparent text-base font-semibold text-slate-900 outline-none placeholder:font-medium
        placeholder:text-slate-400 dark:text-slate-100;
    }
    .stepper {
      @apply rounded-full border border-slate-200 p-1.5 text-slate-600 transition hover:border-brand-500 hover:text-brand-600
        disabled:opacity-40 dark:border-slate-700 dark:text-slate-300;
    }
  `,
})
export class RideSearchForm implements OnInit {
  private readonly router = inject(Router);

  readonly initialFrom = input<string | undefined>(undefined, { alias: 'from' });
  readonly initialTo = input<string | undefined>(undefined, { alias: 'to' });
  readonly initialDate = input<string | undefined>(undefined, { alias: 'date' });
  readonly initialSeats = input<number | undefined>(undefined, { alias: 'seats' });

  protected readonly cities = POPULAR_CITIES;
  protected readonly minDate = todayIso();
  protected readonly from = signal('');
  protected readonly to = signal('');
  protected readonly date = signal(todayIso());
  protected readonly seats = signal(1);
  protected readonly error = signal<string | null>(null);

  ngOnInit(): void {
    this.from.set(this.initialFrom() ?? '');
    this.to.set(this.initialTo() ?? '');
    const d = this.initialDate();
    if (d && d >= this.minDate) this.date.set(d);
    const s = this.initialSeats();
    if (s && s >= 1 && s <= 6) this.seats.set(s);
  }

  protected swap(): void {
    const from = this.from();
    this.from.set(this.to());
    this.to.set(from);
  }

  protected search(event: Event): void {
    event.preventDefault();
    const from = this.from().trim();
    const to = this.to().trim();
    if (!from || !to) {
      this.error.set('Tell us where you are leaving from and where you are going.');
      return;
    }
    if (from.toLowerCase() === to.toLowerCase()) {
      this.error.set('Your start and destination are the same. Pick a different destination.');
      return;
    }
    this.error.set(null);
    void this.router.navigate(['/search'], { queryParams: { from, to, date: this.date(), seats: this.seats() } });
  }
}
