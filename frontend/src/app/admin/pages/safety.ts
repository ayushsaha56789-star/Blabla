import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, DestroyRef, inject, OnInit, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { problemOf } from '../../core/errors';
import { ToastService } from '../../core/toast.service';
import { Avatar } from '../../shared/avatar';
import { Icon } from '../../shared/icon';
import { AdminApi } from '../admin-api';
import { SosRow } from '../admin.models';
import { timeAgo } from '../admin-ui';

/** SOS alerts: take charge of one, call the member (reveal their number on the member page), then resolve it. */
@Component({
  selector: 'app-admin-safety',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [DatePipe, RouterLink, Avatar, Icon],
  template: `
    <header class="flex flex-wrap items-end justify-between gap-4">
      <div>
        <p class="kicker">Trust and safety</p>
        <h1 class="mt-1 text-3xl font-extrabold tracking-tight">SOS alerts</h1>
        <p class="mt-1 text-slate-600 dark:text-slate-400">When a member presses SOS, it shows up here at once. Call them first.</p>
      </div>
      <div class="segmented grid-cols-2" role="tablist" aria-label="Which alerts">
        <button type="button" role="tab" class="segment px-5" [class.segment-on]="open()" [attr.aria-selected]="open()" (click)="show(true)">Open</button>
        <button type="button" role="tab" class="segment px-5" [class.segment-on]="!open()" [attr.aria-selected]="!open()" (click)="show(false)">Resolved</button>
      </div>
    </header>

    @if (error()) { <p class="field-error mt-4" role="alert">{{ error() }}</p> }

    @if (alerts(); as list) {
      @if (!list.length) {
        <div class="card mt-6 p-10 text-center">
          <app-icon name="checkCircle" [size]="32" class="text-brand-600" />
          <p class="mt-3 font-semibold">{{ open() ? 'No open alerts. All quiet.' : 'No resolved alerts yet.' }}</p>
        </div>
      } @else {
        <ul class="mt-6 space-y-4">
          @for (a of list; track a.id) {
            <li class="card p-5" [class]="a.status === 'OPEN' ? 'border-2 border-rose-500' : ''">
              <div class="flex flex-wrap items-start gap-4">
                <a [routerLink]="['/admin/members', a.member.id]" class="flex items-center gap-3">
                  <app-avatar [url]="a.member.avatarUrl" [name]="a.member.displayName" [size]="44" />
                  <span><span class="block font-bold">{{ a.member.displayName }}</span><span class="block font-mono text-xs text-slate-500">{{ a.maskedPhone }}</span></span>
                </a>
                <span class="ml-auto flex flex-wrap items-center gap-2">
                  <span class="badge" [class]="a.status === 'OPEN' ? 'badge-danger' : a.status === 'ACKNOWLEDGED' ? 'badge-warning' : 'badge-muted'">
                    {{ a.status === 'OPEN' ? 'Needs someone now' : a.status === 'ACKNOWLEDGED' ? 'Being handled' : 'Resolved' }}
                  </span>
                  <time class="text-sm text-slate-500" [title]="a.createdAt | date: 'd MMM y, h:mm:ss a'">{{ ago(a.createdAt) }}</time>
                </span>
              </div>
              <dl class="mt-4 grid gap-3 text-sm sm:grid-cols-3">
                <div><dt class="text-slate-500 dark:text-slate-400">Trip</dt>
                  <dd class="font-semibold">@if (a.ride) { <a [routerLink]="['/admin/rides', a.ride.id]" class="hover:underline">{{ a.ride.fromName }} → {{ a.ride.toName }}</a> } @else { Not on a trip }</dd></div>
                <div><dt class="text-slate-500 dark:text-slate-400">Location</dt>
                  <dd class="font-semibold">@if (a.lat != null) { <a [href]="'https://www.google.com/maps?q=' + a.lat + ',' + a.lng" target="_blank" rel="noopener noreferrer" class="hover:underline">{{ a.near ? 'Near ' + a.near : a.lat + ', ' + a.lng }}</a> } @else { Not shared }</dd></div>
                <div><dt class="text-slate-500 dark:text-slate-400">Emergency contacts texted</dt><dd class="font-semibold">{{ a.contactsNotified }}</dd></div>
              </dl>
              @if (a.acknowledgedBy) { <p class="mt-3 text-sm text-slate-600 dark:text-slate-400">Taken by {{ a.acknowledgedBy }} at {{ a.acknowledgedAt | date: 'h:mm a' }}.</p> }
              @if (a.status === 'RESOLVED') {
                <p class="mt-1 text-sm text-slate-600 dark:text-slate-400">Resolved by {{ a.resolvedBy }} at {{ a.resolvedAt | date: 'd MMM, h:mm a' }}.@if (a.notes) { “{{ a.notes }}” }</p>
              } @else {
                <div class="mt-4 flex flex-wrap items-end gap-2">
                  <a [routerLink]="['/admin/members', a.member.id]" class="btn btn-secondary btn-sm"><app-icon name="phone" [size]="16" /> Get their number</a>
                  @if (a.status === 'OPEN') {
                    <button type="button" class="btn btn-primary btn-sm" (click)="acknowledge(a)" [disabled]="busy()">I'm handling this</button>
                  }
                  <label class="min-w-56 flex-1">
                    <span class="sr-only">What happened</span>
                    <input class="input py-2 text-sm" maxlength="1000" placeholder="What happened? (for the record)" [value]="notes()[a.id] ?? ''"
                           (input)="setNote(a.id, $any($event.target).value)" />
                  </label>
                  <button type="button" class="btn btn-secondary btn-sm" (click)="resolve(a)" [disabled]="busy()"><app-icon name="check" [size]="16" /> Resolve</button>
                </div>
              }
            </li>
          }
        </ul>
      }
    } @else if (!error()) {
      <div class="skeleton mt-6 h-40"></div>
    }
  `,
})
export class SafetyPage implements OnInit {
  private readonly api = inject(AdminApi);
  private readonly toast = inject(ToastService);

  protected readonly open = signal(true);
  protected readonly alerts = signal<SosRow[] | null>(null);
  protected readonly notes = signal<Record<string, string>>({});
  protected readonly busy = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly ago = timeAgo;

  constructor() {
    const timer = setInterval(() => {
      if (document.visibilityState === 'visible' && this.open()) void this.load();
    }, 15_000);
    inject(DestroyRef).onDestroy(() => clearInterval(timer));
  }

  ngOnInit(): void {
    void this.load();
  }

  protected show(open: boolean): void {
    this.open.set(open);
    this.alerts.set(null);
    void this.load();
  }

  protected setNote(id: string, value: string): void {
    this.notes.update((n) => ({ ...n, [id]: value }));
  }

  protected async acknowledge(a: SosRow): Promise<void> {
    await this.act(() => this.api.acknowledgeSos(a.id), 'You are handling this alert.');
  }

  protected async resolve(a: SosRow): Promise<void> {
    await this.act(() => this.api.resolveSos(a.id, this.notes()[a.id]?.trim() || null), 'Alert resolved.');
  }

  private async act(call: () => Promise<unknown>, done: string): Promise<void> {
    this.busy.set(true);
    try {
      await call();
      this.toast.success(done);
      await this.load();
    } catch (e) {
      this.toast.error(problemOf(e).detail);
    } finally {
      this.busy.set(false);
    }
  }

  private async load(): Promise<void> {
    try {
      this.alerts.set(await this.api.sosAlerts(this.open()));
      this.error.set(null);
    } catch (e) {
      this.error.set(problemOf(e).detail);
    }
  }
}
