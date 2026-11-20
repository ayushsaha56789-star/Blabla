import { IconName } from '../shared/icon';

/**
 * Member-facing areas that the owner can switch on before they are fully built. Each one gets a menu entry and a
 * "coming soon" page while its switch is on, so the owner can build excitement ahead of launch.
 */
export interface FeaturePage {
  key: string;
  path: string;
  label: string;
  icon: IconName;
  tagline: string;
  points: string[];
  gradient: string;
  /** Built: the menu links to the real pages instead of a "coming soon" page. */
  built?: boolean;
}

export const FEATURE_PAGES: readonly FeaturePage[] = [
  {
    key: 'games',
    path: '/games',
    built: true,
    label: 'Games',
    icon: 'gamepad',
    tagline: 'Chess and ludo for the road — free, fair and fun.',
    points: [
      'Play against the computer: easy, medium or hard',
      'Challenge friends with a room code, or get a quick match',
      'A game room opens for everyone on your ride',
      'Ratings and leaderboards for the competitive ones',
    ],
    gradient: 'from-violet-500 to-fuchsia-500',
  },
  {
    key: 'rentals',
    path: '/rentals',
    built: true,
    label: 'Rent a car',
    icon: 'car',
    tagline: 'Self-drive, or with a driver — from owners near you.',
    points: [
      'Rent by the hour or by the day',
      'Choose self-drive or a car with a driver',
      'Photo check at pickup and return, so nobody argues later',
      'Clear prices, deposits and kilometres up front',
    ],
    gradient: 'from-sky-500 to-indigo-500',
  },
  {
    key: 'drivers',
    path: '/drivers',
    built: true,
    label: 'Hire a driver',
    icon: 'steering',
    tagline: 'Licensed drivers for your own car, in your area.',
    points: [
      'Book a driver by the hour, the day, or for an outstation trip',
      'Only drivers who serve your area and hold the right licence',
      'Ratings, verified badges and SOS on every trip',
      'Drivers: register with your licence and the areas you cover',
    ],
    gradient: 'from-teal-500 to-emerald-600',
  },
  {
    key: 'rewards',
    path: '/rewards',
    label: 'Rewards',
    icon: 'trophy',
    tagline: 'Level up every time you share a ride.',
    points: [
      'XP, levels and badges like Eco Hero and Road Tripper',
      'Weekly challenges and streaks',
      'Invite friends and both get ride credits',
      'See the CO₂ and money you saved, and share your card',
    ],
    gradient: 'from-amber-400 to-orange-500',
  },
  {
    key: 'community',
    path: '/community',
    label: 'Squads & events',
    icon: 'users',
    tagline: 'Ride with your college, your office, your people.',
    points: [
      'Private squads verified by your college or work email',
      'Rides to concerts, fests and matches in one place',
      'Vibe match: find co-travellers who like what you like',
    ],
    gradient: 'from-rose-500 to-pink-500',
  },
  {
    key: 'promotions',
    path: '/promotions',
    label: 'Promote your business',
    icon: 'megaphone',
    tagline: 'Reach thousands of travellers in your city.',
    points: [
      'Create a business or creator profile',
      'Promote your shop, service or project — clearly marked Sponsored',
      'Choose your cities and see how many people you reached',
      'Free for the first month after launch',
    ],
    gradient: 'from-lime-500 to-green-600',
  },
];

export function featurePage(key: string): FeaturePage | undefined {
  return FEATURE_PAGES.find((p) => p.key === key);
}
