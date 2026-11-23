import { ChangeDetectionStrategy, Component, computed, inject, input, OnInit, signal } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { problemOf } from '../../core/errors';
import { CATEGORY_LABELS, MyPromotion, PromoCard, PromoCategory, PromotionsApi, promoStatus, reach } from '../../core/promotions-api';
import { RidesApi } from '../../core/rides-api';
import { addDays, dayOf } from '../../core/rides-format';
import { todayIso } from '../../core/util';
import { Place } from '../../core/rides.models';
import { ToastService } from '../../core/toast.service';
import { Icon } from '../../shared/icon';
import { PlaceInput } from '../../shared/place-input';
import { SponsoredCard } from '../../shared/sponsored-card';

/** Creating or changing a promotion, with a live preview, then sending it for checking. */
@Component({
  selector: 'app-promotion-editor-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, Icon, PlaceInput, SponsoredCard],
  template: `
    <div class="container-page max-w-4xl py-6 sm:py-10">
      <a routerLink="/promotions" class="btn btn-ghost btn-sm -ml-2"><app-icon name="arrowLeft" [size]="16" /> Promotions</a>
      <h1 class="mt-3 text-3xl font-extrabold tracking-tight">{{ id() ? 'Your promotion' : 'Promote your business' }}</h1>
      @if (saved(); as m) {
        <div class="card mt-4 flex flex-wrap items-center gap-3 p-4">
          <span class="badge" [class]="'tone-' + state().tone">{{ state().text }}</span>
          <span class="flex-1 text-sm text-slate-600 dark:text-slate-400">{{ reach(m.views, m.clicks) }}</span>
          @if (m.status === 'DRAFT' || m.status === 'REJECTED') {
            <button type="button" class="btn btn-primary btn-sm" (click)="submit()" [disabled]="busy() || terms()?.free === false">Send for checking</button>
          }
          @if (m.status === 'IN_REVIEW' || m.status === 'APPROVED') {
            <button type="button" class="btn btn-ghost btn-sm text-rose-600 dark:text-rose-400" (click)="stop()" [disabled]="busy()">Stop it</button>
          }
          @if (m.status !== 'IN_REVIEW' && m.status !== 'APPROVED') {
            <button type="button" class="btn btn-ghost btn-sm" (click)="remove()" [disabled]="busy()"><app-icon name="trash" [size]="16" /> Delete</button>
          }
        </div>
        @if (m.reviewNote) { <p class="mt-2 rounded-2xl bg-accent-100/60 p-3 text-sm dark:bg-accent-600/10"><b>From our team:</b> {{ m.reviewNote }}</p> }
      }

      <div class="mt-5 grid grid-cols-1 gap-6 lg:grid-cols-[minmax(0,1fr)_20rem]">
        <form class="card space-y-3 p-5 sm:p-6" (submit)="save($event)">
          <label class="block text-sm"><span class="label text-xs">Business or project</span>
            <input class="input" maxlength="60" [value]="business()" (input)="business.set($any($event.target).value)" placeholder="e.g. Sharma Sweets" /></label>
          <label class="block text-sm"><span class="label text-xs">Headline</span>
            <input class="input" maxlength="80" [value]="title()" (input)="title.set($any($event.target).value)" placeholder="e.g. Hot jalebis near Rajiv Chowk metro" /></label>
          <label class="block text-sm"><span class="label text-xs">A few words ({{ 300 - body().length }} left)</span>
            <textarea class="input min-h-20" maxlength="300" [value]="body()" (input)="body.set($any($event.target).value)" placeholder="What you offer, and why travellers will love it"></textarea></label>
          <label class="block text-sm"><span class="label text-xs">Link (optional, https)</span>
            <input class="input" maxlength="300" [value]="link()" (input)="link.set($any($event.target).value)" placeholder="https://…" /></label>
          <label class="block text-sm"><span class="label text-xs">Category</span>
            <select class="input" (change)="category.set($any($event.target).value)">
              @for (c of categories; track c) { <option [value]="c" [selected]="c === category()">{{ labels[c] }}</option> }
            </select></label>
          <div>
            <span class="label text-xs">Where</span>
            <label class="flex items-center gap-2 text-sm"><input type="checkbox" class="h-4 w-4 accent-brand-600" [checked]="allIndia()" (change)="allIndia.set($any($event.target).checked)" /> All of India</label>
            @if (!allIndia()) {
              <div class="mt-2 flex flex-wrap gap-2">
                @for (c of towns(); track c.id) {
                  <span class="chip gap-1">{{ c.name }}<button type="button" class="-mr-1 rounded-full p-0.5 hover:bg-slate-200 dark:hover:bg-slate-700" (click)="removeTown(c.id)" [attr.aria-label]="'Remove ' + c.name"><app-icon name="close" [size]="14" /></button></span>
                }
              </div>
              @if (towns().length < 10) { <app-place-input class="mt-2 block" inputId="promo-town" [value]="null" (valueChange)="addTown($event)" placeholder="Add a town" /> }
            }
          </div>
          <div class="grid gap-3 sm:grid-cols-2">
            <label class="text-sm"><span class="label text-xs">From</span><input type="date" class="input" [min]="today" [value]="startsOn()" (input)="startsOn.set($any($event.target).value)" /></label>
            <label class="text-sm"><span class="label text-xs">Until (up to 30 days)</span><input type="date" class="input" [min]="startsOn()" [value]="endsOn()" (input)="endsOn.set($any($event.target).value)" /></label>
          </div>
          <p class="hint">{{ offer() }} Every promotion is checked before it goes live: no betting or money games, alcohol, tobacco, adult content, weapons, or promises of guaranteed returns or cures.</p>
          @if (saved()?.status === 'IN_REVIEW' || saved()?.status === 'APPROVED') {
            <p class="hint font-semibold">Saving changes pauses it: send it for checking again afterwards.</p>
          }
          <div class="flex flex-wrap items-center justify-end gap-2">
            @if (saved()) {
              <label class="btn btn-secondary btn-sm cursor-pointer"><app-icon name="camera" [size]="16" /> {{ saved()!.card.imageUrl ? 'Change picture' : 'Add a picture' }}<input type="file" accept="image/*" class="sr-only" (change)="upload($event)" /></label>
            }
            <button type="submit" class="btn btn-primary" [disabled]="busy()">{{ saved() ? 'Save changes' : 'Save draft' }}</button>
          </div>
        </form>
        <aside class="lg:sticky lg:top-24 lg:self-start">
          <p class="kicker mb-2">Preview</p>
          <app-sponsored-card [card]="preview()" [preview]="true" />
          <p class="hint">Members see it at most 3 times a day. You'll see views and clicks, never who saw it.</p>
        </aside>
      </div>
    </div>
  `,
})
export class PromotionEditorPage implements OnInit {
  private readonly api = inject(PromotionsApi);
  private readonly rides = inject(RidesApi);
  private readonly router = inject(Router);
  private readonly toast = inject(ToastService);

  readonly id = input<string | undefined>(undefined);

  protected readonly labels = CATEGORY_LABELS;
  protected readonly reach = reach;
  protected readonly categories = Object.keys(CATEGORY_LABELS) as PromoCategory[];
  protected readonly today = todayIso();
  protected readonly saved = signal<MyPromotion | null>(null);
  protected readonly business = signal('');
  protected readonly title = signal('');
  protected readonly body = signal('');
  protected readonly link = signal('');
  protected readonly category = signal<PromoCategory>('FOOD');
  protected readonly allIndia = signal(false);
  protected readonly towns = signal<Place[]>([]);
  protected readonly startsOn = signal(this.today);
  protected readonly endsOn = signal(addDays(this.today, 13));
  protected readonly busy = signal(false);
  protected readonly terms = signal<{ free: boolean; freeUntil: string | null } | null>(null);

  protected readonly state = computed(() => promoStatus(this.saved()!));
  protected readonly offer = computed(() => {
    const t = this.terms();
    if (t && !t.free) return 'The free launch offer has ended; paid promotions are coming soon.';
    if (t?.freeUntil) return `Free until ${dayOf(t.freeUntil).toLocaleDateString('en-IN', { day: 'numeric', month: 'short', year: 'numeric' })} (launch offer).`;
    return 'Free during the launch offer.';
  });
  protected readonly preview = computed<PromoCard>(() => ({
    id: this.saved()?.card.id ?? 'preview', business: this.business() || 'Your business', title: this.title() || 'Your headline',
    body: this.body() || 'A few words about what you offer.', linkUrl: this.link() || null, category: this.category(),
    imageUrl: this.saved()?.card.imageUrl ?? null,
  }));

  async ngOnInit(): Promise<void> {
    void this.api.options().then((o) => this.terms.set(o), () => undefined);
    const id = this.id();
    if (!id || id === 'new') return;
    try {
      const m = (await this.api.mine()).find((p) => p.card.id === id);
      if (m) await this.fill(m);
      else void this.router.navigateByUrl('/promotions');
    } catch (e) {
      this.toast.error(problemOf(e).detail);
    }
  }

  protected addTown(p: Place | null): void {
    if (p && !this.towns().some((t) => t.id === p.id)) this.towns.set([...this.towns(), p]);
  }

  protected removeTown(id: number): void {
    this.towns.set(this.towns().filter((t) => t.id !== id));
  }

  protected async save(event: Event): Promise<void> {
    event.preventDefault();
    await this.run(async () => {
      const m = await this.api.save(this.saved()?.card.id ?? null, {
        business: this.business().trim(), title: this.title().trim(), body: this.body().trim(), linkUrl: this.link().trim() || null,
        category: this.category(), allIndia: this.allIndia(), placeIds: this.towns().map((t) => t.id), startsOn: this.startsOn(),
        endsOn: this.endsOn(),
      });
      if (!this.saved()) void this.router.navigate(['/promotions', m.card.id], { replaceUrl: true });
      return m;
    }, 'Saved. Add a picture, then send it for checking.');
  }

  protected async upload(event: Event): Promise<void> {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    input.value = '';
    const m = this.saved();
    if (file && m) await this.run(() => this.api.image(m.card.id, file), 'Picture added.');
  }

  protected submit(): Promise<void> {
    return this.run(() => this.api.action(this.saved()!.card.id, 'submit'), 'Sent for checking. We usually reply within a day.');
  }

  protected stop(): Promise<void> {
    return this.run(() => this.api.action(this.saved()!.card.id, 'stop'), 'Stopped.');
  }

  protected async remove(): Promise<void> {
    this.busy.set(true);
    try {
      await this.api.remove(this.saved()!.card.id);
      this.toast.success('Deleted.');
      void this.router.navigateByUrl('/promotions');
    } catch (e) {
      this.toast.error(problemOf(e).detail);
    } finally {
      this.busy.set(false);
    }
  }

  private async fill(m: MyPromotion): Promise<void> {
    this.saved.set(m);
    this.business.set(m.card.business);
    this.title.set(m.card.title);
    this.body.set(m.card.body);
    this.link.set(m.card.linkUrl ?? '');
    this.category.set(m.card.category);
    this.allIndia.set(m.allIndia);
    this.startsOn.set(m.startsOn);
    this.endsOn.set(m.endsOn);
    const places = await Promise.all(m.cities.map((c) => this.rides.place(c.placeId).catch(() => null)));
    this.towns.set(places.filter((p): p is Place => !!p));
  }

  private async run(call: () => Promise<MyPromotion>, done: string): Promise<void> {
    this.busy.set(true);
    try {
      await this.fill(await call());
      this.toast.success(done);
    } catch (e) {
      const p = problemOf(e);
      this.toast.error(p.errors ? Object.values(p.errors)[0] : p.detail);
    } finally {
      this.busy.set(false);
    }
  }
}
