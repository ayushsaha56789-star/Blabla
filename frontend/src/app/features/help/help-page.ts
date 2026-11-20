import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, inject, OnInit, signal } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { AuthService } from '../../core/auth.service';
import { problemOf } from '../../core/errors';
import { CATEGORIES, SupportApi, SupportCategory, TicketSummary, ticketStatus } from '../../core/support-api';
import { Icon } from '../../shared/icon';

interface Faq {
  q: string;
  a: string;
}

const FAQ: ReadonlyArray<{ title: string; items: Faq[] }> = [
  {
    title: 'Booking a seat',
    items: [
      { q: 'How do I book?', a: 'Search for your trip, open a ride and choose where you get in and out. With "Instant booking" your seat is confirmed straight away; otherwise the driver accepts or declines your request (requests lapse after 12 hours or at pickup time).' },
      { q: 'How and when do I pay?', a: 'You pay the driver directly, in cash or by UPI, on the day of the trip. The price shown is per seat for your part of the route. Online payment comes later.' },
      { q: 'Can I cancel?', a: 'Yes. Open the ride (or My rides) and tap "Cancel my booking" any time before your pickup time. Please cancel as early as you can so someone else can take the seat.' },
      { q: 'When do I get the driver\'s number?', a: 'As soon as your seat is confirmed. Until then, use chat on SaathChalo. Phone numbers are only ever shared between a driver and passengers with a confirmed seat.' },
    ],
  },
  {
    title: 'Offering a ride',
    items: [
      { q: 'Why is there a price limit?', a: 'SaathChalo is for sharing travel costs, not for running a taxi business. We suggest about ₹2 per km per seat and never allow more than ₹3.5 per km. This keeps rides affordable and within the rules for car pooling.' },
      { q: 'What are stops?', a: 'Towns you pass through. Passengers there can book just part of your trip, and pay their share of the distance. More stops usually means more bookings.' },
      { q: 'How do I answer booking requests?', a: 'You\'ll see a badge on "My rides". Open the ride to accept or decline each request. You can message passengers before you decide.' },
    ],
  },
  {
    title: 'Safety',
    items: [
      { q: 'What is the SOS button?', a: 'Around your trip, the ride page shows a red SOS button. It helps you call 112, alerts SaathChalo support, and texts your emergency contacts a link to follow your trip. Add emergency contacts on your profile page.' },
      { q: 'How does "Share my trip" work?', a: 'It gives you a private link to send to family. They can see your route, the driver and car, and (if you turn it on) your live location. The link stops working a day after the trip.' },
      { q: 'What are ladies-only rides?', a: 'Women drivers can offer rides that only women can see and book.' },
      { q: 'How do I report or block someone?', a: 'Use "Report" or "Block" on their profile, in a chat, or on a ride. Our team reads every report within 24 hours. Blocking stops both of you from messaging or booking each other.' },
    ],
  },
  {
    title: 'Your account and privacy',
    items: [
      { q: 'Who can see my details?', a: 'Other members see your first name, photo, bio, age (not your birth date), preferences and reviews. Your phone number is shared only with your co-travellers once a seat is confirmed.' },
      { q: 'How do I download my data or delete my account?', a: 'Open the Privacy centre from the menu. You can download everything we hold about you, or delete your account (you have 30 days to change your mind).' },
    ],
  },
];

/** Help: answers to common questions, and a way to write to us. */
@Component({
  selector: 'app-help-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [DatePipe, RouterLink, Icon],
  template: `
    <div class="container-page max-w-3xl py-8 sm:py-12">
      <header class="text-center">
        <h1 class="text-3xl font-extrabold tracking-tight sm:text-4xl">How can we help?</h1>
        <p class="mt-2 text-slate-600 dark:text-slate-400">Quick answers below, or write to us and a real person will reply.</p>
      </header>

      <div class="mt-6 flex items-center gap-3 rounded-2xl bg-rose-50 p-4 text-sm text-rose-900 dark:bg-rose-500/10 dark:text-rose-100" role="note">
        <app-icon name="shieldAlert" [size]="22" class="shrink-0" />
        <p class="flex-1">In danger? Call <a href="tel:112" class="font-bold underline">112</a>. During a trip, use the SOS button on your ride page.</p>
      </div>

      @for (group of faq; track group.title) {
        <section class="mt-8" [attr.aria-labelledby]="'faq-' + $index">
          <h2 [id]="'faq-' + $index" class="kicker mb-3">{{ group.title }}</h2>
          <div class="card divide-y divide-slate-100 dark:divide-slate-800">
            @for (item of group.items; track item.q) {
              <details class="group p-5">
                <summary class="flex cursor-pointer list-none items-center justify-between gap-4 font-semibold">
                  {{ item.q }} <app-icon name="chevronDown" [size]="18" class="shrink-0 text-slate-400 transition group-open:rotate-180" />
                </summary>
                <p class="mt-3 text-slate-600 dark:text-slate-400">{{ item.a }}</p>
              </details>
            }
          </div>
        </section>
      }

      <section class="card mt-10 p-6 sm:p-8" aria-labelledby="contact-heading" id="contact">
        <h2 id="contact-heading" class="text-xl font-bold">Still need help?</h2>
        @if (auth.isLoggedIn()) {
          <form class="mt-4 space-y-4" (submit)="send($event)" novalidate>
            <div>
              <label for="h-cat" class="label">What is it about?</label>
              <select id="h-cat" class="input" [value]="category() ?? ''" (change)="category.set($any($event.target).value)">
                <option value="" disabled>Choose…</option>
                @for (c of categoryKeys; track c) { <option [value]="c">{{ categories[c] }}</option> }
              </select>
            </div>
            <div>
              <label for="h-subject" class="label">Subject</label>
              <input id="h-subject" class="input" maxlength="120" [value]="subject()" (input)="subject.set($any($event.target).value)" placeholder="e.g. My driver didn't turn up" />
            </div>
            <div>
              <label for="h-body" class="label">Tell us more</label>
              <textarea id="h-body" class="input min-h-32" maxlength="4000" [value]="body()" (input)="body.set($any($event.target).value)"
                        placeholder="What happened, when, and which ride? The more we know, the faster we can help."></textarea>
            </div>
            @if (error()) { <p class="field-error" role="alert">{{ error() }}</p> }
            <div class="flex flex-wrap items-center justify-between gap-3">
              <p class="text-xs text-slate-500 dark:text-slate-400">We reply within 24 hours. Formal complaints are handled by our <a routerLink="/legal/grievance" class="underline">grievance officer</a>.</p>
              <button type="submit" class="btn btn-primary" [disabled]="sending()">{{ sending() ? 'Sending…' : 'Send' }}</button>
            </div>
          </form>
        } @else {
          <p class="mt-2 text-slate-600 dark:text-slate-400">Log in so we can see your rides and reply to you.</p>
          <a routerLink="/login" [queryParams]="{ returnUrl: '/help' }" class="btn btn-primary mt-4">Log in to contact us</a>
        }
      </section>

      @if (tickets().length) {
        <section class="mt-8" aria-labelledby="mine-heading">
          <h2 id="mine-heading" class="kicker mb-3">Your requests</h2>
          <ul class="card divide-y divide-slate-100 dark:divide-slate-800">
            @for (t of tickets(); track t.id) {
              <li>
                <a [routerLink]="['/help/requests', t.id]" class="flex items-center gap-3 p-4 hover:bg-slate-50 dark:hover:bg-slate-800/50">
                  <span class="min-w-0 flex-1">
                    <span class="block truncate font-semibold">{{ t.subject }}</span>
                    <span class="block text-xs text-slate-500 dark:text-slate-400">{{ categories[t.category] }} · updated {{ t.updatedAt | date: 'd MMM, h:mm a' }}</span>
                  </span>
                  @if (t.newReply) {
                    <span class="badge bg-brand-600 text-white">New reply</span>
                  } @else {
                    <span class="badge" [class]="'tone-' + status(t.status).tone">{{ status(t.status).text }}</span>
                  }
                </a>
              </li>
            }
          </ul>
        </section>
      }
    </div>
  `,
})
export class HelpPage implements OnInit {
  private readonly api = inject(SupportApi);
  private readonly router = inject(Router);
  protected readonly auth = inject(AuthService);

  protected readonly faq = FAQ;
  protected readonly categories = CATEGORIES;
  protected readonly categoryKeys = Object.keys(CATEGORIES) as SupportCategory[];
  protected readonly status = ticketStatus;
  protected readonly category = signal<SupportCategory | null>(null);
  protected readonly subject = signal('');
  protected readonly body = signal('');
  protected readonly sending = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly tickets = signal<TicketSummary[]>([]);

  async ngOnInit(): Promise<void> {
    if (!this.auth.isLoggedIn()) return;
    try {
      this.tickets.set(await this.api.mine());
    } catch {
      // The FAQ still works.
    }
  }

  protected async send(event: Event): Promise<void> {
    event.preventDefault();
    const category = this.category();
    if (!category || !this.subject().trim() || !this.body().trim()) {
      this.error.set('Please choose a topic, add a subject and tell us what happened.');
      return;
    }
    this.sending.set(true);
    this.error.set(null);
    try {
      const t = await this.api.create(category, this.subject().trim(), this.body().trim(), null);
      void this.router.navigate(['/help/requests', t.id]);
    } catch (e) {
      const p = problemOf(e);
      this.error.set(p.errors ? Object.values(p.errors)[0] : p.detail);
    } finally {
      this.sending.set(false);
    }
  }
}
