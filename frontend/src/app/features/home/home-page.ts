import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { POPULAR_ROUTES } from '../../core/cities';
import { FEATURE_PAGES } from '../../core/feature-pages';
import { FeaturesService } from '../../core/features.service';
import { Icon, IconName } from '../../shared/icon';
import { RideSearchForm } from '../../shared/ride-search-form';

@Component({
  selector: 'app-home-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, Icon, RideSearchForm],
  templateUrl: './home-page.html',
})
export class HomePage {
  protected readonly features = inject(FeaturesService);
  protected readonly routes = POPULAR_ROUTES;
  /** Extra areas (games, rentals, …) the owner has switched on. */
  protected readonly extras = computed(() => FEATURE_PAGES.filter((p) => this.features.enabledKeys().has(p.key)));

  protected readonly benefits: ReadonlyArray<{ icon: IconName; title: string; text: string; tint: string }> = [
    { icon: 'rupee', title: 'Pay less, travel better', text: 'Split fuel and toll costs with co-travellers. Usually far cheaper than a cab.', tint: 'bg-brand-50 text-brand-700 dark:bg-brand-950 dark:text-brand-300' },
    { icon: 'shield', title: 'Verified members', text: 'Every member verifies their phone number. Profiles, photos and reviews show who you ride with.', tint: 'bg-sky-50 text-sky-700 dark:bg-sky-950 dark:text-sky-300' },
    { icon: 'woman', title: 'Ladies-only rides', text: 'Women can choose to travel only with other women, for extra peace of mind.', tint: 'bg-fuchsia-50 text-fuchsia-700 dark:bg-fuchsia-950 dark:text-fuchsia-300' },
    { icon: 'leaf', title: 'Kinder to the planet', text: 'Every shared seat is one less car on the road, and a little less pollution.', tint: 'bg-accent-100 text-accent-600 dark:bg-accent-600/15 dark:text-accent-300' },
  ];

  protected readonly steps: ReadonlyArray<{ icon: IconName; title: string; text: string }> = [
    { icon: 'search', title: 'Search your route', text: 'Enter where you are leaving from, where you are going and when.' },
    { icon: 'checkCircle', title: 'Book a seat', text: 'Compare drivers, prices and profiles. Book the ride that suits you.' },
    { icon: 'car', title: 'Travel together', text: 'Meet at the pickup point, enjoy the ride, and rate each other after.' },
  ];
}
