import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, inject, OnDestroy, OnInit, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { problemOf } from '../../core/errors';
import { ToastService } from '../../core/toast.service';
import { DOCUMENTS, RejectReason } from '../../core/verification-api';
import { Avatar } from '../../shared/avatar';
import { Icon } from '../../shared/icon';
import { AdminApi } from '../admin-api';
import { StaffIdCheck } from '../admin.models';
import { timeAgo } from '../admin-ui';

type Filter = 'PENDING' | 'APPROVED' | 'REJECTED';

/** Short labels for staff; members see a fuller explanation. */
const REASONS: Record<RejectReason, string> = {
  UNREADABLE: 'Blurry, dark or cut off',
  NAME_MISMATCH: "Name doesn't match the profile",
  NOT_ACCEPTED: 'Not an accepted ID',
  EXPIRED: 'Expired',
  AADHAAR_NOT_MASKED: 'Full Aadhaar number visible (photo is deleted at once)',
  OTHER: 'Other (explain in the note)',
};

/** ID checks: compare the photo with the profile, then approve or reject. */
@Component({
  selector: 'app-admin-verifications',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [DatePipe, RouterLink, Avatar, Icon],
  template: `
    <header class="flex flex-wrap items-end justify-between gap-4">
      <div>
        <p class="kicker">Trust and safety</p>
        <h1 class="mt-1 text-3xl font-extrabold tracking-tight">ID checks</h1>
        <p class="mt-1 text-slate-600 dark:text-slate-400">Check that the name matches the profile, the photo is clear, the ID is valid, and an Aadhaar is masked.</p>
      </div>
      <div class="segmented grid-cols-3" role="tablist" aria-label="Which checks">
        @for (f of filters; track f.key) {
          <button type="button" role="tab" class="segment px-3 sm:px-4" [class.segment-on]="filter() === f.key" [attr.aria-selected]="filter() === f.key" (click)="show(f.key)">{{ f.label }}</button>
        }
      </div>
    </header>

    @if (error()) { <p class="field-error mt-4" role="alert">{{ error() }}</p> }

    @if (checks(); as list) {
      @if (!list.length) {
        <div class="card mt-6 p-10 text-center">
          <app-icon name="checkCircle" [size]="32" class="text-brand-600" />
          <p class="mt-3 font-semibold">{{ filter() === 'PENDING' ? 'No ID checks waiting.' : 'Nothing here yet.' }}</p>
        </div>
      } @else {
        <ul class="mt-6 space-y-4">
          @for (c of list; track c.id) {
            <li class="card p-5">
              <div class="flex flex-wrap items-start gap-4">
                <a [routerLink]="['/admin/members', c.member.id]" class="flex min-w-0 flex-1 items-center gap-3">
                  <app-avatar [url]="c.member.avatarUrl" [name]="c.member.displayName" [size]="44" />
                  <span class="min-w-0">
                    <span class="block truncate font-semibold">{{ c.member.displayName }}</span>
                    <span class="block text-xs text-slate-500 dark:text-slate-400">Sent {{ ago(c.submittedAt) }}</span>
                  </span>
                </a>
                <span class="badge badge-muted"><app-icon name="idCard" [size]="14" /> {{ documents[c.documentType] }}</span>
              </div>

              <dl class="mt-4 grid gap-x-6 gap-y-2 rounded-2xl bg-slate-50 p-4 text-sm sm:grid-cols-3 dark:bg-slate-800/50">
                <div><dt class="text-xs text-slate-500 dark:text-slate-400">Name on profile</dt><dd class="font-semibold">{{ c.fullName ?? '—' }}</dd></div>
                <div><dt class="text-xs text-slate-500 dark:text-slate-400">Gender</dt><dd class="font-semibold">{{ gender(c.gender) }}</dd></div>
                <div><dt class="text-xs text-slate-500 dark:text-slate-400">Date of birth</dt>
                  <dd class="font-semibold">@if (c.dateOfBirth) { {{ c.dateOfBirth | date: 'd MMM y' }} ({{ c.age }}) } @else { — }</dd></div>
              </dl>

              @if (photos()[c.id]; as url) {
                <figure class="mt-4">
                  <img [src]="url" [alt]="'ID photo sent by ' + c.member.displayName" class="max-h-[28rem] w-full rounded-2xl border border-slate-200 bg-slate-100 object-contain dark:border-slate-700 dark:bg-slate-900" />
                  <figcaption class="mt-2 flex justify-between text-xs text-slate-500 dark:text-slate-400">
                    <span>Viewing is recorded in the audit log.</span>
                    <button type="button" class="font-semibold underline" (click)="hidePhoto(c.id)">Hide photo</button>
                  </figcaption>
                </figure>
              } @else if (c.photoAvailable) {
                <button type="button" class="btn btn-secondary btn-sm mt-4" (click)="showPhoto(c)" [disabled]="loadingPhoto() === c.id">
                  <app-icon name="eye" [size]="16" /> {{ loadingPhoto() === c.id ? 'Loading…' : 'Show photo' }}
                </button>
              } @else {
                <p class="mt-4 text-sm text-slate-500 dark:text-slate-400">The photo has been deleted (30 days after the decision, or at once for a full Aadhaar number).</p>
              }

              @if (c.status === 'PENDING') {
                <div class="mt-4 flex flex-wrap items-end gap-2 border-t border-slate-100 pt-4 dark:border-slate-800">
                  <button type="button" class="btn btn-primary btn-sm" (click)="approve(c)" [disabled]="busy()"><app-icon name="check" [size]="16" /> Approve</button>
                  <span class="mx-1 hidden text-sm text-slate-400 sm:inline">or</span>
                  <label class="text-sm">
                    <span class="label text-xs">Reject because</span>
                    <select class="input py-2 text-sm" [value]="reasons()[c.id] ?? ''" (change)="setReason(c.id, $any($event.target).value)">
                      <option value="" disabled>Choose…</option>
                      @for (r of reasonKeys; track r) { <option [value]="r">{{ reasonLabels[r] }}</option> }
                    </select>
                  </label>
                  <label class="min-w-48 flex-1 text-sm">
                    <span class="label text-xs">Note for the member (optional)</span>
                    <input class="input py-2 text-sm" maxlength="300" [value]="notes()[c.id] ?? ''" (input)="setNote(c.id, $any($event.target).value)" />
                  </label>
                  <button type="button" class="btn btn-ghost btn-sm text-rose-600 dark:text-rose-400" (click)="reject(c)" [disabled]="busy() || !reasons()[c.id]">Reject</button>
                </div>
              } @else {
                <p class="mt-4 text-sm text-slate-600 dark:text-slate-400">
                  <span class="badge" [class]="c.status === 'APPROVED' ? 'badge-success' : 'badge-danger'">{{ c.status === 'APPROVED' ? 'Approved' : 'Rejected' }}</span>
                  <span class="ml-2">by {{ c.decidedBy ?? 'staff' }}, {{ c.decidedAt | date: 'd MMM, h:mm a' }}</span>
                  @if (c.rejectReason) { <span class="block mt-1">{{ reasonLabels[c.rejectReason] }}@if (c.rejectNote) { — “{{ c.rejectNote }}” }</span> }
                </p>
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
export class VerificationsPage implements OnInit, OnDestroy {
  private readonly api = inject(AdminApi);
  private readonly toast = inject(ToastService);

  protected readonly filters: { key: Filter; label: string }[] = [
    { key: 'PENDING', label: 'Waiting' },
    { key: 'APPROVED', label: 'Approved' },
    { key: 'REJECTED', label: 'Rejected' },
  ];
  protected readonly documents = DOCUMENTS;
  protected readonly reasonLabels = REASONS;
  protected readonly reasonKeys = Object.keys(REASONS) as RejectReason[];
  protected readonly ago = timeAgo;

  protected readonly filter = signal<Filter>('PENDING');
  protected readonly checks = signal<StaffIdCheck[] | null>(null);
  protected readonly error = signal<string | null>(null);
  protected readonly busy = signal(false);
  protected readonly loadingPhoto = signal<string | null>(null);
  protected readonly photos = signal<Record<string, string>>({});
  protected readonly reasons = signal<Record<string, RejectReason>>({});
  protected readonly notes = signal<Record<string, string>>({});

  ngOnInit(): void {
    void this.load();
  }

  ngOnDestroy(): void {
    Object.values(this.photos()).forEach((url) => URL.revokeObjectURL(url));
  }

  protected gender(g: string | null): string {
    return g === 'FEMALE' ? 'Woman' : g === 'MALE' ? 'Man' : g ? 'Other' : '—';
  }

  protected show(filter: Filter): void {
    this.filter.set(filter);
    this.checks.set(null);
    void this.load();
  }

  protected async showPhoto(c: StaffIdCheck): Promise<void> {
    this.loadingPhoto.set(c.id);
    try {
      const blob = await this.api.verificationPhoto(c.id);
      this.photos.update((p) => ({ ...p, [c.id]: URL.createObjectURL(blob) }));
    } catch (e) {
      this.toast.error(problemOf(e).status === 404 ? 'This photo has been deleted.' : problemOf(e).detail);
    } finally {
      this.loadingPhoto.set(null);
    }
  }

  protected hidePhoto(id: string): void {
    const url = this.photos()[id];
    if (url) URL.revokeObjectURL(url);
    this.photos.update((p) => {
      const { [id]: _, ...rest } = p;
      return rest;
    });
  }

  protected setReason(id: string, reason: RejectReason): void {
    this.reasons.update((r) => ({ ...r, [id]: reason }));
  }

  protected setNote(id: string, note: string): void {
    this.notes.update((n) => ({ ...n, [id]: note }));
  }

  protected async approve(c: StaffIdCheck): Promise<void> {
    await this.act(() => this.api.approveVerification(c.id), `${c.member.displayName} is now ID verified.`, c.id);
  }

  protected async reject(c: StaffIdCheck): Promise<void> {
    const reason = this.reasons()[c.id];
    if (!reason) return;
    const note = (this.notes()[c.id] ?? '').trim() || null;
    await this.act(() => this.api.rejectVerification(c.id, reason, note), 'Rejected. The member can try again.', c.id);
  }

  private async act(call: () => Promise<unknown>, done: string, id: string): Promise<void> {
    this.busy.set(true);
    try {
      await call();
      this.toast.success(done);
      this.hidePhoto(id);
      await this.load();
    } catch (e) {
      this.toast.error(problemOf(e).detail);
    } finally {
      this.busy.set(false);
    }
  }

  private async load(): Promise<void> {
    try {
      this.checks.set(await this.api.verifications(this.filter()));
      this.error.set(null);
    } catch (e) {
      this.error.set(problemOf(e).detail);
    }
  }
}
