import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, inject, OnInit, signal } from '@angular/core';
import { problemOf } from '../../core/errors';
import { fromLocalInput, toLocalInput } from '../../core/rentals-api';
import { RidesApi } from '../../core/rides-api';
import { Place } from '../../core/rides.models';
import { EventCard } from '../../core/social-api';
import { ToastService } from '../../core/toast.service';
import { Icon } from '../../shared/icon';
import { PlaceInput } from '../../shared/place-input';
import { AdminApi } from '../admin-api';

/** Events (concerts, fests, matches) members can find rides to. */
@Component({
  selector: 'app-admin-events',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [DatePipe, Icon, PlaceInput],
  template: `
    <header>
      <p class="kicker">Community</p>
      <h1 class="mt-1 text-3xl font-extrabold tracking-tight">Events</h1>
      <p class="mt-1 text-slate-600 dark:text-slate-400">Members see each event with the rides going there (within 25 km, up to two days before it starts).</p>
    </header>

    <form class="card mt-6 grid gap-3 p-5 sm:grid-cols-2" (submit)="save($event)" aria-labelledby="event-form-heading">
      <h2 id="event-form-heading" class="font-bold sm:col-span-2">{{ editing() ? 'Change the event' : 'Add an event' }}</h2>
      <label class="text-sm"><span class="label text-xs">Title</span><input class="input" maxlength="100" [value]="title()" (input)="title.set($any($event.target).value)" placeholder="e.g. Jaipur Literature Festival" /></label>
      <label class="text-sm"><span class="label text-xs">Venue</span><input class="input" maxlength="150" [value]="venue()" (input)="venue.set($any($event.target).value)" placeholder="e.g. Hotel Clarks Amer" /></label>
      <div class="text-sm"><label class="label text-xs" for="ev-place">Town</label><app-place-input inputId="ev-place" [(value)]="place" placeholder="Town or city" /></div>
      <label class="text-sm"><span class="label text-xs">Starts</span><input type="datetime-local" class="input" [value]="startsAt()" (input)="startsAt.set($any($event.target).value)" /></label>
      <label class="text-sm sm:col-span-2"><span class="label text-xs">About (optional)</span><textarea class="input min-h-16" maxlength="1000" [value]="description()" (input)="description.set($any($event.target).value)"></textarea></label>
      <div class="flex justify-end gap-2 sm:col-span-2">
        @if (editing()) { <button type="button" class="btn btn-ghost" (click)="reset()">Cancel changes</button> }
        <button type="submit" class="btn btn-primary" [disabled]="busy()">{{ editing() ? 'Save event' : 'Add event' }}</button>
      </div>
    </form>

    @if (events(); as list) {
      <ul class="mt-6 space-y-3">
        @for (e of list; track e.id) {
          <li class="card flex flex-wrap items-center gap-3 p-4">
            <span class="min-w-0 flex-1"><span class="block font-bold">{{ e.title }}</span>
              <span class="block text-sm text-slate-600 dark:text-slate-400">{{ e.startsAt | date: 'EEE d MMM y, h:mm a' }} · {{ e.venue }}, {{ e.placeName }}</span></span>
            <span class="badge" [class]="e.status === 'LIVE' ? 'badge-success' : 'badge-muted'">{{ e.status === 'LIVE' ? 'Live' : 'Cancelled' }}</span>
            @if (e.status === 'LIVE') {
              <button type="button" class="btn btn-ghost btn-sm" (click)="edit(e)"><app-icon name="edit" [size]="16" /> Edit</button>
              <button type="button" class="btn btn-ghost btn-sm text-rose-600 dark:text-rose-400" (click)="cancel(e)" [disabled]="busy()">Cancel event</button>
            }
          </li>
        } @empty { <li class="card p-6 text-center text-sm text-slate-600 dark:text-slate-400">No events yet.</li> }
      </ul>
    } @else { <div class="skeleton mt-6 h-24"></div> }
  `,
})
export class EventsAdminPage implements OnInit {
  private readonly api = inject(AdminApi);
  private readonly rides = inject(RidesApi);
  private readonly toast = inject(ToastService);

  protected readonly events = signal<EventCard[] | null>(null);
  protected readonly editing = signal<string | null>(null);
  protected readonly title = signal('');
  protected readonly venue = signal('');
  protected readonly place = signal<Place | null>(null);
  protected readonly startsAt = signal('');
  protected readonly description = signal('');
  protected readonly busy = signal(false);

  async ngOnInit(): Promise<void> {
    await this.load();
  }

  protected async edit(e: EventCard): Promise<void> {
    this.editing.set(e.id);
    this.title.set(e.title);
    this.venue.set(e.venue);
    this.startsAt.set(toLocalInput(new Date(e.startsAt)));
    this.place.set(await this.rides.place(e.placeId).catch(() => null));
    window.scrollTo({ top: 0, behavior: 'smooth' });
  }

  protected reset(): void {
    this.editing.set(null);
    this.title.set('');
    this.venue.set('');
    this.place.set(null);
    this.startsAt.set('');
    this.description.set('');
  }

  protected async save(event: Event): Promise<void> {
    event.preventDefault();
    const starts = fromLocalInput(this.startsAt());
    const place = this.place();
    if (!place || !starts) {
      this.toast.error('Choose the town and the start time.');
      return;
    }
    await this.act(() => this.api.saveEvent(this.editing(), {
      title: this.title().trim(), venue: this.venue().trim(), placeId: place.id, startsAt: starts, description: this.description().trim() || null,
    }), this.editing() ? 'Event saved.' : 'Event added.');
    this.reset();
  }

  protected cancel(e: EventCard): Promise<void> {
    return this.act(() => this.api.cancelEvent(e.id), 'Event cancelled.');
  }

  private async act(call: () => Promise<unknown>, done: string): Promise<void> {
    this.busy.set(true);
    try {
      await call();
      this.toast.success(done);
      await this.load();
    } catch (e) {
      const p = problemOf(e);
      this.toast.error(p.errors ? Object.values(p.errors)[0] : p.detail);
    } finally {
      this.busy.set(false);
    }
  }

  private async load(): Promise<void> {
    try {
      this.events.set(await this.api.events());
    } catch (e) {
      this.toast.error(problemOf(e).detail);
      this.events.set([]);
    }
  }
}
