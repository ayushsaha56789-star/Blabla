import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, input, numberAttribute } from '@angular/core';
import { RouterLink } from '@angular/router';
import { Icon } from '../../shared/icon';
import { RideSearchForm } from '../../shared/ride-search-form';

/** Ride search. Phase 1 shows the search bar; results arrive with rides in phase 2. */
@Component({
  selector: 'app-search-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [DatePipe, RouterLink, Icon, RideSearchForm],
  template: `
    <div class="container-page py-8 sm:py-12">
      <h1 class="sr-only">Find a ride</h1>
      <app-ride-search-form [from]="from()" [to]="to()" [date]="date()" [seats]="seats()" />

      <section class="card mx-auto mt-8 max-w-2xl p-8 text-center motion-safe:animate-fade-up sm:p-12">
        <span class="mx-auto flex h-16 w-16 items-center justify-center rounded-3xl bg-brand-50 text-brand-600 dark:bg-brand-950 dark:text-brand-400">
          <app-icon name="route" [size]="30" />
        </span>
        @if (from() && to()) {
          <h2 class="mt-5 text-2xl font-extrabold tracking-tight">{{ from() }} → {{ to() }}</h2>
          <p class="mt-1 text-sm text-slate-500 dark:text-slate-400">
            @if (date()) { {{ date() | date: 'EEE, d MMM' }} · }{{ seats() || 1 }} {{ (seats() || 1) === 1 ? 'passenger' : 'passengers' }}
          </p>
        } @else {
          <h2 class="mt-5 text-2xl font-extrabold tracking-tight">Where are you headed?</h2>
        }
        <p class="mx-auto mt-4 max-w-md text-slate-600 dark:text-slate-400">
          Ride listings arrive in the next update. Soon you'll see every driver going your way right here,
          with prices, timings and profiles.
        </p>
        <a routerLink="/publish" class="btn btn-secondary mt-6"><app-icon name="car" [size]="18" /> Driving this route? Offer a ride</a>
      </section>
    </div>
  `,
})
export class SearchPage {
  readonly from = input<string>();
  readonly to = input<string>();
  readonly date = input<string>();
  readonly seats = input<number | undefined, unknown>(undefined, {
    transform: (v: unknown) => (v == null || v === '' ? undefined : numberAttribute(v)),
  });
}
