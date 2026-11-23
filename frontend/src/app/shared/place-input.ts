import { CdkConnectedOverlay, CdkOverlayOrigin, ConnectedPosition } from '@angular/cdk/overlay';
import {
  afterNextRender, ChangeDetectionStrategy, Component, computed, effect, ElementRef, inject, Injector, input, model, output, signal, untracked,
  viewChild,
} from '@angular/core';
import { problemOf } from '../core/errors';
import { Place } from '../core/rides.models';
import { RidesApi } from '../core/rides-api';
import { ToastService } from '../core/toast.service';
import { Icon } from './icon';

let nextId = 0;

/**
 * A city/town picker for India. Type any name people use ("Bangalore", "Gurgaon", "दिल्ली") and pick from the list
 * with the mouse or the arrow keys. Popular cities show up before anything is typed. If someone types a name and
 * moves on without picking, the best match is picked for them.
 */
@Component({
  selector: 'app-place-input',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [Icon, CdkOverlayOrigin, CdkConnectedOverlay],
  host: { class: 'relative block' },
  template: `
    <input #box [id]="inputId() || id + '-input'" class="scroll-mt-24" [class]="inputClass()" [placeholder]="placeholder()" autocomplete="off"
           cdkOverlayOrigin #origin="cdkOverlayOrigin"
           role="combobox" aria-autocomplete="list" [attr.aria-expanded]="panelOpen()" [attr.aria-controls]="id + '-list'"
           [attr.aria-activedescendant]="open() && active() >= 0 ? id + '-opt-' + active() : null"
           [attr.aria-label]="ariaLabel() || null" [attr.aria-invalid]="invalid() || null" spellcheck="false"
           [value]="text()" (input)="onInput(box.value)" (focus)="onFocus()" (blur)="onBlur()" (keydown)="onKey($event)" />
    <!-- The list floats in the overlay layer above the whole page: below the field, or above it when there's no room. -->
    <ng-template cdkConnectedOverlay [cdkConnectedOverlayOrigin]="origin" [cdkConnectedOverlayOpen]="panelOpen()"
                 [cdkConnectedOverlayPositions]="positions" [cdkConnectedOverlayWidth]="width()"
                 [cdkConnectedOverlayViewportMargin]="8" [cdkConnectedOverlayPush]="true" (detach)="open.set(false)">
    @if (open() && (results().length || allowLocation())) {
      <ul [id]="id + '-list'" role="listbox" [attr.aria-label]="ariaLabel() || 'Places'"
          class="card w-full animate-pop overflow-y-auto overscroll-contain p-1.5 shadow-2xl shadow-slate-900/15" [style.max-height.px]="maxHeight()">
        @if (allowLocation() && !text().trim()) {
          <li role="option" [id]="id + '-opt-loc'" aria-selected="false"
              class="flex cursor-pointer items-center gap-3 rounded-xl px-3 py-2.5 text-sm font-semibold text-brand-700 hover:bg-brand-50 dark:text-brand-300 dark:hover:bg-brand-500/10"
              (mousedown)="$event.preventDefault(); useMyLocation()">
            <app-icon name="pin" [size]="18" />
            @if (locating()) { Finding where you are… } @else { Use my current location }
          </li>
        }
        @if (!text().trim() && results().length) {
          <li role="presentation" class="kicker px-3 pt-2 pb-1">Popular</li>
        }
        @for (p of results(); track p.id; let i = $index) {
          <li role="option" [id]="id + '-opt-' + i" [attr.aria-selected]="i === active()"
              class="flex cursor-pointer items-center gap-3 rounded-xl px-3 py-2.5"
              [class]="i === active() ? 'bg-slate-100 dark:bg-slate-800' : 'hover:bg-slate-50 dark:hover:bg-slate-800/60'"
              (mousedown)="$event.preventDefault(); choose(p)" (mousemove)="active.set(i)">
            <app-icon name="pin" [size]="16" class="text-slate-400" />
            <span class="min-w-0">
              <span class="block truncate text-sm font-semibold text-slate-900 dark:text-slate-100">{{ p.name }}</span>
              <span class="block truncate text-xs text-slate-500 dark:text-slate-400">{{ p.state }}</span>
            </span>
          </li>
        }
      </ul>
    } @else if (noMatch()) {
      <div class="card w-full p-4 text-sm text-slate-500 shadow-xl dark:text-slate-400" role="status">
        No town called “<span translate="no">{{ text().trim() }}</span>”. Try the nearest city.
      </div>
    }
    </ng-template>
  `,
})
export class PlaceInput {
  private readonly api = inject(RidesApi);
  private readonly toast = inject(ToastService);

  readonly value = model<Place | null>(null);
  readonly placeholder = input('City or town');
  readonly inputClass = input('input');
  readonly inputId = input<string>();
  readonly ariaLabel = input<string>();
  readonly allowLocation = input(false);
  readonly invalid = input(false);
  /** Places to leave out of the list, e.g. the other end of the route. */
  readonly exclude = input<readonly number[]>([]);
  readonly picked = output<Place>();

  protected readonly id = `place-${nextId++}`;
  protected readonly text = signal('');
  protected readonly results = signal<Place[]>([]);
  protected readonly open = signal(false);
  protected readonly active = signal(-1);
  protected readonly loading = signal(false);
  protected readonly locating = signal(false);
  /** The query the current results belong to. */
  protected readonly searched = signal<string | null>(null);
  /** The list is as wide as the field, and never narrower than 16rem. */
  protected readonly width = signal(256);
  /** Tall enough for the room below the field (so it opens below, where people expect it): 180–320px. */
  protected readonly maxHeight = signal(320);

  protected readonly noMatch = computed(() =>
    this.open() && this.text().trim().length >= 2 && !this.loading() && this.searched() === this.text().trim());
  protected readonly panelOpen = computed(() =>
    (this.open() && (this.results().length > 0 || this.allowLocation())) || this.noMatch());
  protected readonly positions: ConnectedPosition[] = [
    { originX: 'start', originY: 'bottom', overlayX: 'start', overlayY: 'top', offsetY: 8 },
    { originX: 'start', originY: 'top', overlayX: 'start', overlayY: 'bottom', offsetY: -8 },
    { originX: 'end', originY: 'bottom', overlayX: 'end', overlayY: 'top', offsetY: 8 },
    { originX: 'end', originY: 'top', overlayX: 'end', overlayY: 'bottom', offsetY: -8 },
  ];

  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);
  private readonly injector = inject(Injector);
  private readonly overlay = viewChild(CdkConnectedOverlay);
  private readonly box = viewChild.required<ElementRef<HTMLInputElement>>('box');
  private timer: ReturnType<typeof setTimeout> | undefined;
  private requestNo = 0;

  constructor() {
    // Show the chosen place's name when the value is set from outside (swap, prefill).
    effect(() => {
      const place = this.value();
      untracked(() => {
        if (place && this.text() !== place.name) this.text.set(place.name);
        if (!place && this.text() && !this.open()) this.text.set('');
      });
    });
    // The list's height changes as results arrive: place it again once drawn, so it opens below the field whenever
    // it fits there.
    effect(() => {
      this.results();
      this.maxHeight();
      this.noMatch();
      if (!this.panelOpen()) return;
      afterNextRender(() => this.overlay()?.overlayRef?.updatePosition(), { injector: this.injector });
    });
  }

  focus(): void {
    this.box().nativeElement.focus();
  }

  protected onInput(raw: string): void {
    this.text.set(raw);
    const current = this.value();
    if (current && current.name !== raw) this.value.set(null);
    this.measure();
    this.open.set(true);
    clearTimeout(this.timer);
    this.timer = setTimeout(() => void this.load(raw.trim()), 150);
  }

  protected onFocus(): void {
    // Phones: bring the field up under the top bar, so the list has room above the keyboard.
    if (window.innerWidth < 640) this.box().nativeElement.scrollIntoView({ block: 'start' });
    this.measure();
    this.open.set(true);
    if (!this.text().trim()) void this.load('');
    else if (this.searched() !== this.text().trim()) void this.load(this.text().trim());
    queueMicrotask(() => this.box().nativeElement.select());
  }

  protected onBlur(): void {
    setTimeout(() => {
      if (document.activeElement === this.box().nativeElement) return;
      const typed = this.text().trim();
      if (!this.value() && typed && this.searched() === typed && this.results().length) this.choose(this.results()[0]);
      this.open.set(false);
    }, 120);
  }

  protected onKey(event: KeyboardEvent): void {
    const count = this.results().length;
    switch (event.key) {
      case 'ArrowDown':
        event.preventDefault();
        this.open.set(true);
        if (count) this.active.set((this.active() + 1) % count);
        break;
      case 'ArrowUp':
        event.preventDefault();
        if (count) this.active.set(this.active() <= 0 ? count - 1 : this.active() - 1);
        break;
      case 'Enter':
        if (this.open() && count) {
          event.preventDefault();
          this.choose(this.results()[Math.max(0, this.active())]);
        }
        break;
      case 'Escape':
        if (this.open()) {
          event.stopPropagation();
          this.open.set(false);
        }
        break;
      case 'Tab':
        if (this.open() && count && this.text().trim() && !this.value()) this.choose(this.results()[Math.max(0, this.active())]);
        break;
    }
  }

  protected choose(place: Place): void {
    this.value.set(place);
    this.text.set(place.name);
    this.open.set(false);
    this.active.set(-1);
    this.picked.emit(place);
  }

  protected useMyLocation(): void {
    if (!('geolocation' in navigator)) {
      this.toast.error("Your browser can't share your location. Please type your city instead.");
      return;
    }
    this.locating.set(true);
    navigator.geolocation.getCurrentPosition(
      async (pos) => {
        try {
          this.choose(await this.api.nearestPlace(pos.coords.latitude, pos.coords.longitude));
        } catch (e) {
          this.toast.error(problemOf(e).detail);
        } finally {
          this.locating.set(false);
        }
      },
      () => {
        this.locating.set(false);
        this.toast.error("We couldn't get your location. Please type your city instead.");
      },
      { enableHighAccuracy: false, timeout: 10_000, maximumAge: 300_000 },
    );
  }

  private measure(): void {
    this.width.set(Math.max(256, Math.round(this.host.nativeElement.getBoundingClientRect().width)));
    const screen = window.visualViewport?.height ?? window.innerHeight;
    // Room below the field, less the 8px gap above the list and the overlay's screen-edge margin (which the CDK
    // counts twice when checking the fit), with a little slack for rounding.
    const below = screen - this.box().nativeElement.getBoundingClientRect().bottom - 32;
    this.maxHeight.set(Math.round(Math.min(320, Math.max(180, below))));
  }

  private async load(q: string): Promise<void> {
    const no = ++this.requestNo;
    this.loading.set(true);
    try {
      const found = await this.api.searchPlaces(q, 8);
      if (no !== this.requestNo) return;
      const skip = new Set(this.exclude());
      this.results.set(found.filter((p) => !skip.has(p.id)));
      this.searched.set(q);
      this.active.set(q ? 0 : -1);
    } catch {
      if (no === this.requestNo) this.results.set([]);
    } finally {
      if (no === this.requestNo) this.loading.set(false);
    }
  }
}
