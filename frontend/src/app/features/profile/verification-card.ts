import { ChangeDetectionStrategy, Component, effect, inject, OnDestroy, signal, untracked } from '@angular/core';
import { AuthService } from '../../core/auth.service';
import { problemOf } from '../../core/errors';
import { FeaturesService } from '../../core/features.service';
import { ToastService } from '../../core/toast.service';
import { DOCUMENTS, DocumentType, REJECT_REASONS, shrinkPhoto, VerificationApi, VerificationStatus } from '../../core/verification-api';
import { Icon } from '../../shared/icon';

/** Phone, email and ID checks. Verified members get more bookings, so the card explains each step simply. */
@Component({
  selector: 'app-verification',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [Icon],
  host: { class: 'block' },
  template: `
    <section class="card p-6 sm:p-8" aria-labelledby="verify-heading">
      <div class="flex items-start gap-3">
        <span class="flex h-11 w-11 shrink-0 items-center justify-center rounded-2xl bg-brand-50 text-brand-700 dark:bg-brand-500/15 dark:text-brand-300"><app-icon name="shield" [size]="22" /></span>
        <div>
          <h2 id="verify-heading" class="text-lg font-bold">Verification</h2>
          <p class="text-sm text-slate-500 dark:text-slate-400">Verified members get more bookings, and co-travellers feel safer. Each check takes a minute.</p>
        </div>
      </div>

      @if (status(); as s) {
        <ul class="mt-5 divide-y divide-slate-100 dark:divide-slate-800">
          <li class="flex items-center gap-3 py-4">
            <app-icon name="phone" [size]="20" class="shrink-0 text-slate-400" />
            <span class="flex-1 font-semibold">Phone number</span>
            <span class="badge badge-success"><app-icon name="checkCircle" [size]="14" /> Verified</span>
          </li>

          @if (features.isOn('verification.email')) {
            <li class="py-4">
              <div class="flex flex-wrap items-center gap-3">
                <app-icon name="mail" [size]="20" class="shrink-0 text-slate-400" />
                <span class="min-w-0 flex-1"><span class="block font-semibold">Email</span>
                  @if (s.email) { <span class="block truncate text-sm text-slate-500 dark:text-slate-400">{{ s.email }}</span> }</span>
                @if (s.emailVerified) {
                  <span class="badge badge-success"><app-icon name="checkCircle" [size]="14" /> Verified</span>
                } @else if (!s.email) {
                  <span class="text-sm text-slate-500 dark:text-slate-400">Add your email above first</span>
                } @else if (!codeSentTo()) {
                  <button type="button" class="btn btn-secondary btn-sm" (click)="sendCode()" [disabled]="busy()">Send me a code</button>
                }
              </div>
              @if (!s.emailVerified && codeSentTo()) {
                <form class="mt-3 rounded-2xl bg-slate-50 p-4 dark:bg-slate-800/50" (submit)="confirmCode($event)">
                  <label for="email-code" class="label">Enter the 6-digit code we sent to {{ codeSentTo() }}</label>
                  <div class="flex flex-wrap gap-2">
                    <input id="email-code" class="input w-40 font-mono text-lg tracking-[0.3em]" inputmode="numeric" autocomplete="one-time-code" maxlength="6"
                           [value]="code()" (input)="typed($event)" />
                    <button type="submit" class="btn btn-primary btn-sm" [disabled]="busy() || code().length !== 6">Confirm</button>
                    <button type="button" class="btn btn-ghost btn-sm" (click)="sendCode()" [disabled]="busy() || resendIn() > 0">
                      {{ resendIn() > 0 ? 'Resend in ' + resendIn() + 's' : 'Send a new code' }}
                    </button>
                  </div>
                  @if (devCode()) {
                    <p class="mt-3 text-sm"><span class="font-semibold">Developer mode:</span> no email is sent locally. Your code is
                      <button type="button" class="font-mono font-bold tracking-widest underline decoration-dotted" (click)="code.set(devCode()!)">{{ devCode() }}</button></p>
                  }
                </form>
              }
            </li>
          }

          @if (features.isOn('verification.id')) {
            <li class="py-4">
              <div class="flex flex-wrap items-center gap-3">
                <app-icon name="idCard" [size]="20" class="shrink-0 text-slate-400" />
                <span class="flex-1 font-semibold">ID</span>
                @if (s.idVerified) {
                  <span class="badge badge-success"><app-icon name="checkCircle" [size]="14" /> Verified</span>
                } @else if (s.latestIdCheck?.status === 'PENDING') {
                  <span class="badge badge-warning"><app-icon name="clock" [size]="14" /> Checking</span>
                }
              </div>
              @if (!s.idVerified) {
                @if (s.latestIdCheck?.status === 'PENDING') {
                  <p class="mt-2 text-sm text-slate-600 dark:text-slate-400">We're checking your {{ documents[s.latestIdCheck!.documentType].toLowerCase() }}. This usually takes less than a day.</p>
                } @else {
                  @if (s.latestIdCheck?.status === 'REJECTED') {
                    <div class="mt-3 rounded-2xl bg-rose-50 p-3 text-sm text-rose-800 dark:bg-rose-500/10 dark:text-rose-200" role="status">
                      <p class="font-semibold">We couldn't verify your {{ documents[s.latestIdCheck!.documentType].toLowerCase() }}.</p>
                      <p>{{ reasons[s.latestIdCheck!.rejectReason ?? 'OTHER'] }}@if (s.latestIdCheck!.rejectNote) { {{ ' ' + s.latestIdCheck!.rejectNote }} }</p>
                      <p class="mt-1">You can try again below.</p>
                    </div>
                  }
                  <form class="mt-3 space-y-3 rounded-2xl bg-slate-50 p-4 dark:bg-slate-800/50" (submit)="sendId($event)">
                    <div>
                      <label for="id-type" class="label">Which ID?</label>
                      <select id="id-type" class="input" [value]="docType() ?? ''" (change)="docType.set($any($event.target).value || null)">
                        <option value="" disabled>Choose…</option>
                        @for (d of docKeys; track d) { <option [value]="d">{{ documents[d] }}</option> }
                      </select>
                    </div>
                    <div>
                      <label for="id-photo" class="label">Photo of the front</label>
                      <input id="id-photo" type="file" accept="image/jpeg,image/png" capture="environment" class="input py-2 text-sm"
                             (change)="pick($event)" />
                    </div>
                    @if (preview(); as url) {
                      <img [src]="url" alt="Your ID photo" class="max-h-48 rounded-xl border border-slate-200 object-contain dark:border-slate-700" />
                    }
                    <ul class="space-y-1 text-xs text-slate-500 dark:text-slate-400">
                      <li>• Your name must match your profile, and the photo must be sharp and well lit.</li>
                      <li>• Aadhaar: use a <b>masked Aadhaar</b>, with only the last 4 digits showing. Never send the full number.</li>
                      <li>• Only our checking team sees the photo. It's stored encrypted and deleted 30 days after we decide.</li>
                    </ul>
                    <button type="submit" class="btn btn-primary btn-sm" [disabled]="busy() || !docType() || !photo()">{{ busy() ? 'Sending…' : 'Send for checking' }}</button>
                  </form>
                }
              }
            </li>
          }

          @if (features.isOn('rentals') && features.isOn('verification.id') && s.idVerified) {
            <li class="py-4">
              <div class="flex flex-wrap items-center gap-3">
                <app-icon name="steering" [size]="20" class="shrink-0 text-slate-400" />
                <span class="min-w-0 flex-1"><span class="block font-semibold">Driving licence</span>
                  <span class="block text-xs text-slate-500 dark:text-slate-400">Needed to rent a car for self-drive</span></span>
                @if (s.licenceVerified) {
                  <span class="badge badge-success"><app-icon name="checkCircle" [size]="14" /> Verified</span>
                } @else if (s.latestIdCheck?.status === 'PENDING' && s.latestIdCheck?.documentType === 'DRIVING_LICENCE') {
                  <span class="badge badge-warning"><app-icon name="clock" [size]="14" /> Checking</span>
                } @else if (!licenceForm()) {
                  <button type="button" class="btn btn-secondary btn-sm" (click)="openLicence()">Add licence</button>
                }
              </div>
              @if (licenceForm() && !s.licenceVerified) {
                <form class="mt-3 space-y-3 rounded-2xl bg-slate-50 p-4 dark:bg-slate-800/50" (submit)="sendId($event)">
                  <label class="block text-sm"><span class="label">Photo of the front of your licence</span>
                    <input type="file" accept="image/jpeg,image/png" capture="environment" class="input py-2 text-sm" (change)="pick($event)" /></label>
                  @if (preview(); as url) { <img [src]="url" alt="Your licence photo" class="max-h-40 rounded-xl border border-slate-200 object-contain dark:border-slate-700" /> }
                  <div class="flex justify-end gap-2">
                    <button type="button" class="btn btn-ghost btn-sm" (click)="licenceForm.set(false)">Cancel</button>
                    <button type="submit" class="btn btn-primary btn-sm" [disabled]="busy() || !photo()">{{ busy() ? 'Sending…' : 'Send for checking' }}</button>
                  </div>
                </form>
              }
            </li>
          }
        </ul>
      } @else if (loadError()) {
        <p class="mt-5 text-sm text-rose-600 dark:text-rose-400" role="alert">{{ loadError() }}</p>
      } @else {
        <div class="skeleton mt-5 h-28"></div>
      }
    </section>
  `,
})
export class VerificationCard implements OnDestroy {
  private readonly api = inject(VerificationApi);
  private readonly toast = inject(ToastService);
  private readonly auth = inject(AuthService);
  protected readonly features = inject(FeaturesService);

  protected readonly documents = DOCUMENTS;
  protected readonly docKeys = Object.keys(DOCUMENTS) as DocumentType[];
  protected readonly reasons = REJECT_REASONS;

  protected readonly status = signal<VerificationStatus | null>(null);
  protected readonly loadError = signal<string | null>(null);
  protected readonly busy = signal(false);
  protected readonly codeSentTo = signal<string | null>(null);
  protected readonly code = signal('');
  protected readonly devCode = signal<string | null>(null);
  protected readonly resendIn = signal(0);
  protected readonly docType = signal<DocumentType | null>(null);
  protected readonly photo = signal<Blob | null>(null);
  protected readonly preview = signal<string | null>(null);
  protected readonly licenceForm = signal(false);
  private timer: ReturnType<typeof setInterval> | null = null;

  constructor() {
    // Reload when the profile changes (for example a new email address).
    effect(() => {
      const me = this.auth.user();
      void me?.email;
      void me?.emailVerified;
      untracked(() => void this.load());
    });
  }

  /** A member whose ID is verified with another document adds their driving licence. */
  protected openLicence(): void {
    this.docType.set('DRIVING_LICENCE');
    this.licenceForm.set(true);
  }

  ngOnDestroy(): void {
    if (this.timer) clearInterval(this.timer);
    const url = this.preview();
    if (url) URL.revokeObjectURL(url);
  }

  protected async sendCode(): Promise<void> {
    this.busy.set(true);
    try {
      const sent = await this.api.sendEmailCode();
      this.codeSentTo.set(sent.sentTo);
      this.devCode.set(sent.devCode ?? null);
      this.code.set('');
      this.countdown(sent.resendInSeconds);
      setTimeout(() => document.getElementById('email-code')?.focus());
    } catch (e) {
      const p = problemOf(e);
      this.toast.error(p.detail);
    } finally {
      this.busy.set(false);
    }
  }

  /** Keeps digits only, so pasted codes like "123 456" work. */
  protected typed(event: Event): void {
    const input = event.target as HTMLInputElement;
    const digits = input.value.replace(/\D/g, '').slice(0, 6);
    input.value = digits;
    this.code.set(digits);
  }

  protected async confirmCode(event: Event): Promise<void> {
    event.preventDefault();
    this.busy.set(true);
    try {
      this.status.set(await this.api.confirmEmail(this.code()));
      this.codeSentTo.set(null);
      this.devCode.set(null);
      const me = this.auth.user();
      if (me) this.auth.setUser({ ...me, emailVerified: true });
      this.toast.success('Your email is verified.');
    } catch (e) {
      const p = problemOf(e);
      this.toast.error(p.errors ? Object.values(p.errors)[0] : p.detail);
    } finally {
      this.busy.set(false);
    }
  }

  protected async pick(event: Event): Promise<void> {
    const file = (event.target as HTMLInputElement).files?.[0];
    const old = this.preview();
    if (old) URL.revokeObjectURL(old);
    if (!file) {
      this.photo.set(null);
      this.preview.set(null);
      return;
    }
    const small = await shrinkPhoto(file);
    this.photo.set(small);
    this.preview.set(URL.createObjectURL(small));
  }

  protected async sendId(event: Event): Promise<void> {
    event.preventDefault();
    const type = this.docType();
    const photo = this.photo();
    if (!type || !photo) return;
    this.busy.set(true);
    try {
      const check = await this.api.submitId(type, photo);
      this.status.update((s) => (s ? { ...s, latestIdCheck: check } : s));
      this.photo.set(null);
      const url = this.preview();
      if (url) URL.revokeObjectURL(url);
      this.preview.set(null);
      this.licenceForm.set(false);
      this.toast.success("Thanks! We'll check it, usually within a day.");
    } catch (e) {
      this.toast.error(problemOf(e).detail);
    } finally {
      this.busy.set(false);
    }
  }

  private countdown(seconds: number): void {
    if (this.timer) clearInterval(this.timer);
    this.resendIn.set(seconds);
    this.timer = setInterval(() => {
      this.resendIn.update((s) => Math.max(0, s - 1));
      if (this.resendIn() === 0 && this.timer) {
        clearInterval(this.timer);
        this.timer = null;
      }
    }, 1000);
  }

  private async load(): Promise<void> {
    try {
      this.status.set(await this.api.status());
      this.loadError.set(null);
    } catch (e) {
      this.loadError.set(problemOf(e).detail);
    }
  }
}
