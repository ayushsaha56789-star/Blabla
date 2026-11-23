import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, inject, OnInit, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { FeaturesService } from '../../core/features.service';
import { clickRate, MyPromotion, PromoCard, PromotionsApi, promoStatus, reach } from '../../core/promotions-api';
import { Icon } from '../../shared/icon';
import { SponsoredCard } from '../../shared/sponsored-card';

/** Promotions: discover local businesses, and manage your own promotions. */
@Component({
  selector: 'app-promotions-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [DatePipe, RouterLink, Icon, SponsoredCard],
  template: `
    <div class="container-page max-w-4xl py-8 sm:py-10">
      <header class="flex flex-wrap items-end justify-between gap-4">
        <div>
          <p class="kicker">Promotions</p>
          <h1 class="mt-1 text-3xl font-extrabold tracking-tight">Local businesses &amp; creators</h1>
          <p class="mt-1 text-slate-600 dark:text-slate-400">Shops, services and projects from SaathChalo members near you.</p>
        </div>
        @if (features.isOn('promotions.self_serve')) {
          <a routerLink="/promotions/new" class="btn btn-primary btn-sm"><app-icon name="megaphone" [size]="16" /> Promote your business</a>
        }
      </header>
      @if (features.isOn('promotions.self_serve')) {
        <div class="segmented mt-6 grid-cols-2 sm:max-w-xs" role="tablist" aria-label="Show">
          <button type="button" role="tab" class="segment" [class.segment-on]="tab() === 'discover'" [attr.aria-selected]="tab() === 'discover'" (click)="tab.set('discover')">Discover</button>
          <button type="button" role="tab" class="segment" [class.segment-on]="tab() === 'mine'" [attr.aria-selected]="tab() === 'mine'" (click)="showMine()">My promotions</button>
        </div>
      }

      @if (tab() === 'discover') {
        @if (feed(); as list) {
          @if (list.length) {
            <ul class="mt-6 grid grid-cols-1 gap-4 sm:grid-cols-2">
              @for (p of list; track p.id) { <li><app-sponsored-card [card]="p" /></li> }
            </ul>
          } @else { <p class="card mt-6 p-8 text-center text-sm text-slate-600 dark:text-slate-400">Nothing promoted near you right now.</p> }
        } @else { <div class="skeleton mt-6 h-32"></div> }
      } @else {
        @if (mine(); as list) {
          @if (list.length) {
            <ul class="mt-6 space-y-3">
              @for (m of list; track m.card.id) {
                <li>
                  <a [routerLink]="['/promotions', m.card.id]" class="card flex flex-wrap items-center gap-3 p-4 transition hover:shadow-md">
                    <span class="min-w-0 flex-1"><span class="block truncate font-bold">{{ m.card.title }}</span>
                      <span class="block text-sm text-slate-500 dark:text-slate-400">{{ m.startsOn | date: 'd MMM' }} – {{ m.endsOn | date: 'd MMM' }} · {{ m.allIndia ? 'All of India' : towns(m) }}</span></span>
                    <span class="text-right text-sm"><span class="block font-bold">{{ reach(m.views, m.clicks) }}</span>
                      <span class="block text-xs text-slate-500">{{ rate(m) }} clicked</span></span>
                    <span class="badge" [class]="'tone-' + status(m).tone">{{ status(m).text }}</span>
                  </a>
                </li>
              }
            </ul>
          } @else {
            <div class="card mt-6 p-10 text-center">
              <app-icon name="megaphone" [size]="32" class="text-slate-400" />
              <p class="mt-3 font-semibold">Reach travellers in your city.</p>
              <p class="mt-1 text-sm text-slate-600 dark:text-slate-400">A shop, a tiffin service, a class, your band: promote it to members in the towns you choose.</p>
              <a routerLink="/promotions/new" class="btn btn-primary mt-4">Create a promotion</a>
            </div>
          }
        } @else { <div class="skeleton mt-6 h-24"></div> }
      }
    </div>
  `,
})
export class PromotionsPage implements OnInit {
  private readonly api = inject(PromotionsApi);
  protected readonly features = inject(FeaturesService);

  protected readonly tab = signal<'discover' | 'mine'>('discover');
  protected readonly feed = signal<PromoCard[] | null>(null);
  protected readonly mine = signal<MyPromotion[] | null>(null);
  protected readonly status = promoStatus;
  protected readonly reach = reach;

  async ngOnInit(): Promise<void> {
    this.feed.set(await this.api.feed('DISCOVER', [], 20).catch(() => []));
  }

  protected async showMine(): Promise<void> {
    this.tab.set('mine');
    this.mine.set(await this.api.mine().catch(() => []));
  }

  protected towns(m: MyPromotion): string {
    return m.cities.map((c) => c.name).join(', ') || 'No towns yet';
  }

  protected rate(m: MyPromotion): string {
    return clickRate(m.views, m.clicks);
  }
}
