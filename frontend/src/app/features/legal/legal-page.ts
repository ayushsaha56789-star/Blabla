import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, effect, inject, input, signal, untracked } from '@angular/core';
import { RouterLink, RouterLinkActive } from '@angular/router';
import { LegalContacts, PrivacyApi } from '../../core/privacy-api';
import { Icon } from '../../shared/icon';
import { LEGAL_DOCS, LEGAL_DRAFT, LEGAL_UPDATED, LegalDocKey } from './legal-docs';

/** Terms, privacy policy, community guidelines and the grievance officer, at /legal/:doc. */
@Component({
  selector: 'app-legal-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [DatePipe, RouterLink, RouterLinkActive, Icon],
  template: `
    <div class="container-page max-w-5xl py-8 sm:py-12">
      <div class="grid gap-8 lg:grid-cols-[14rem_1fr]">
        <nav aria-label="Policies" class="-mx-4 flex gap-2 overflow-x-auto px-4 pb-1 lg:mx-0 lg:flex-col lg:gap-1 lg:overflow-visible lg:px-0">
          @for (d of docs; track d.key) {
            <a [routerLink]="['/legal', d.key]" routerLinkActive="!bg-brand-50 !text-brand-700 dark:!bg-brand-500/10 dark:!text-brand-300"
               class="btn btn-ghost btn-sm shrink-0 justify-start whitespace-nowrap">{{ d.title }}</a>
          }
          <a routerLink="/privacy-centre" class="btn btn-ghost btn-sm shrink-0 justify-start whitespace-nowrap"><app-icon name="lock" [size]="16" /> Privacy centre</a>
        </nav>

        @if (current(); as d) {
          <article class="min-w-0">
            <header>
              <div class="flex flex-wrap items-center gap-2">
                <p class="kicker">Updated {{ updated | date: 'd MMMM y' }}</p>
                @if (draft) { <span class="badge badge-warning">Draft · to be reviewed before launch</span> }
              </div>
              <h1 class="mt-2 text-3xl font-extrabold tracking-tight sm:text-4xl">{{ d.title }}</h1>
              <p class="mt-2 text-lg text-slate-600 dark:text-slate-400">{{ d.summary }}</p>
            </header>

            @if (d.key === 'grievance') {
              <section class="card mt-6 p-6" aria-labelledby="officer-heading">
                <h2 id="officer-heading" class="text-lg font-bold">Contact details</h2>
                @if (contacts(); as c) {
                  @if (c.grievanceOfficer || c.grievanceEmail) {
                    <dl class="mt-4 grid gap-3 text-sm sm:grid-cols-[10rem_1fr]">
                      @if (c.grievanceOfficer) { <dt class="text-slate-500 dark:text-slate-400">Grievance officer</dt><dd class="font-semibold">{{ c.grievanceOfficer }}</dd> }
                      @if (c.grievanceEmail) { <dt class="text-slate-500 dark:text-slate-400">Email</dt><dd class="font-semibold break-all">{{ c.grievanceEmail }}</dd> }
                      @if (c.company) { <dt class="text-slate-500 dark:text-slate-400">Company</dt><dd>{{ c.company }}</dd> }
                      @if (c.address) { <dt class="text-slate-500 dark:text-slate-400">Address</dt><dd class="whitespace-pre-line">{{ c.address }}</dd> }
                    </dl>
                  } @else {
                    <p class="mt-2 text-slate-600 dark:text-slate-400">We are appointing our grievance officer before launch. Until then, please write to us through the Help centre and choose "A formal complaint (grievance)".</p>
                  }
                } @else {
                  <div class="skeleton mt-4 h-16"></div>
                }
                <a routerLink="/help" fragment="contact" class="btn btn-primary btn-sm mt-5">Write to us</a>
              </section>
            }

            @for (s of d.sections; track s.heading) {
              <section class="mt-8">
                <h2 class="text-xl font-bold">{{ s.heading }}</h2>
                @for (p of s.paragraphs ?? []; track $index) { <p class="mt-3 leading-relaxed text-slate-700 dark:text-slate-300">{{ p }}</p> }
                @if (s.points?.length) {
                  <ul class="mt-3 space-y-2">
                    @for (p of s.points; track $index) {
                      <li class="flex gap-3 leading-relaxed text-slate-700 dark:text-slate-300">
                        <span class="mt-2.5 h-1.5 w-1.5 shrink-0 rounded-full bg-brand-500" aria-hidden="true"></span><span>{{ p }}</span>
                      </li>
                    }
                  </ul>
                }
              </section>
            }

            <p class="mt-10 rounded-2xl bg-slate-50 p-4 text-sm text-slate-600 dark:bg-slate-800/50 dark:text-slate-400">
              Questions? <a routerLink="/help" class="font-semibold text-brand-700 underline dark:text-brand-400">Visit the Help centre</a> or contact our <a routerLink="/legal/grievance" class="font-semibold text-brand-700 underline dark:text-brand-400">grievance officer</a>.
            </p>
          </article>
        } @else {
          <p class="card p-8 text-center">We couldn't find that page. Choose one from the list.</p>
        }
      </div>
    </div>
  `,
})
export class LegalPage {
  private readonly api = inject(PrivacyApi);

  readonly doc = input<string>('terms');

  protected readonly docs = Object.values(LEGAL_DOCS);
  protected readonly draft = LEGAL_DRAFT;
  protected readonly updated = LEGAL_UPDATED;
  protected readonly current = computed(() => LEGAL_DOCS[this.doc() as LegalDocKey] ?? null);
  protected readonly contacts = signal<LegalContacts | null>(null);

  constructor() {
    effect(() => {
      if (this.doc() === 'grievance' && !untracked(this.contacts)) {
        void this.api.legalContacts()
          .then((c) => this.contacts.set(c))
          .catch(() => this.contacts.set({ company: null, grievanceOfficer: null, grievanceEmail: null, address: null }));
      }
    });
  }
}
