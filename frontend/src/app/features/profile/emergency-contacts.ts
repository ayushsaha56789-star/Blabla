import { ChangeDetectionStrategy, Component, inject, OnInit, signal } from '@angular/core';
import { problemOf } from '../../core/errors';
import { EmergencyContact, SafetyApi } from '../../core/safety-api';
import { ToastService } from '../../core/toast.service';
import { Icon } from '../../shared/icon';
import { PhonePipe } from '../../shared/phone.pipe';

/** Up to three people texted if the member presses SOS during a trip. */
@Component({
  selector: 'app-emergency-contacts',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [Icon, PhonePipe],
  host: { class: 'block' },
  template: `
    <section class="card p-6 sm:p-8" aria-labelledby="sos-contacts-heading">
      <div class="flex items-start gap-3">
        <span class="flex h-11 w-11 shrink-0 items-center justify-center rounded-2xl bg-rose-50 text-rose-600 dark:bg-rose-500/15 dark:text-rose-300"><app-icon name="shieldAlert" [size]="22" /></span>
        <div>
          <h2 id="sos-contacts-heading" class="text-lg font-bold">Emergency contacts</h2>
          <p class="text-sm text-slate-500 dark:text-slate-400">If you press SOS during a trip, we text them a link to follow your trip. Up to 3 people.</p>
        </div>
      </div>
      @if (contacts() === null) {
        <div class="skeleton mt-5 h-14"></div>
      } @else {
        @if (contacts()!.length) {
          <ul class="mt-5 space-y-2">
            @for (c of contacts(); track c.id) {
              <li class="flex items-center gap-3 rounded-2xl bg-slate-50 px-4 py-3 dark:bg-slate-800/60">
                <app-icon name="user" [size]="18" class="text-slate-400" />
                <span class="min-w-0 flex-1"><span class="block font-semibold">{{ c.name }}</span><span class="block text-sm text-slate-500 dark:text-slate-400">{{ c.phone | phone }}</span></span>
                <button type="button" class="btn btn-ghost btn-icon text-rose-600 dark:text-rose-400" (click)="remove(c)" [disabled]="busy()" [attr.aria-label]="'Remove ' + c.name"><app-icon name="trash" [size]="18" /></button>
              </li>
            }
          </ul>
        }
        @if (contacts()!.length < 3) {
          <form class="mt-5 grid gap-3 sm:grid-cols-[1fr_1fr_auto] sm:items-start" (submit)="add($event)" novalidate>
            <div>
              <label for="ec-name" class="label text-xs">Name</label>
              <input id="ec-name" class="input" maxlength="60" autocomplete="off" placeholder="e.g. Amma" [value]="name()" (input)="name.set($any($event.target).value)" />
            </div>
            <div>
              <label for="ec-phone" class="label text-xs">Mobile number</label>
              <input id="ec-phone" class="input" inputmode="tel" autocomplete="off" placeholder="98765 43210" [value]="phone()" (input)="phone.set($any($event.target).value)" />
            </div>
            <button type="submit" class="btn btn-secondary sm:mt-6" [disabled]="busy()"><app-icon name="plus" [size]="18" /> Add</button>
          </form>
          @if (error()) { <p class="field-error" role="alert">{{ error() }}</p> }
        }
      }
    </section>
  `,
})
export class EmergencyContacts implements OnInit {
  private readonly api = inject(SafetyApi);
  private readonly toast = inject(ToastService);

  protected readonly contacts = signal<EmergencyContact[] | null>(null);
  protected readonly name = signal('');
  protected readonly phone = signal('');
  protected readonly busy = signal(false);
  protected readonly error = signal<string | null>(null);

  async ngOnInit(): Promise<void> {
    try {
      this.contacts.set(await this.api.contacts());
    } catch (e) {
      this.contacts.set([]);
      this.error.set(problemOf(e).detail);
    }
  }

  protected async add(event: Event): Promise<void> {
    event.preventDefault();
    if (!this.name().trim() || !this.phone().trim()) {
      this.error.set('Please enter their name and mobile number.');
      return;
    }
    this.busy.set(true);
    this.error.set(null);
    try {
      const c = await this.api.addContact(this.name().trim(), this.phone().trim());
      this.contacts.update((list) => [...(list ?? []), c]);
      this.name.set('');
      this.phone.set('');
      this.toast.success(`${c.name} is now an emergency contact.`);
    } catch (e) {
      const p = problemOf(e);
      this.error.set(p.errors ? Object.values(p.errors)[0] : p.detail);
    } finally {
      this.busy.set(false);
    }
  }

  protected async remove(c: EmergencyContact): Promise<void> {
    this.busy.set(true);
    try {
      await this.api.removeContact(c.id);
      this.contacts.update((list) => (list ?? []).filter((x) => x.id !== c.id));
    } catch (e) {
      this.toast.error(problemOf(e).detail);
    } finally {
      this.busy.set(false);
    }
  }
}
