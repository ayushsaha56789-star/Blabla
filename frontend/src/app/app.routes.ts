import { Routes } from '@angular/router';
import { authGuard, guestGuard, profileCompleteGuard } from './core/auth.guards';
import { FEATURE_PAGES } from './core/feature-pages';
import { featureGuard } from './core/features.service';

export const routes: Routes = [
  {
    path: '',
    title: 'SaathChalo — Share rides across India',
    loadComponent: () => import('./features/home/home-page').then((m) => m.HomePage),
  },
  {
    path: 'login',
    title: 'Log in · SaathChalo',
    canActivate: [guestGuard],
    loadComponent: () => import('./features/auth/login-page').then((m) => m.LoginPage),
  },
  {
    path: 'onboarding',
    title: 'Welcome · SaathChalo',
    canActivate: [authGuard],
    loadComponent: () => import('./features/auth/onboarding-page').then((m) => m.OnboardingPage),
  },
  {
    path: 'profile',
    title: 'My profile · SaathChalo',
    canActivate: [authGuard],
    loadComponent: () => import('./features/profile/profile-page').then((m) => m.ProfilePage),
  },
  {
    path: 'u/:id',
    title: 'Member profile · SaathChalo',
    loadComponent: () => import('./features/profile/public-profile-page').then((m) => m.PublicProfilePage),
  },
  {
    path: 'search',
    title: 'Find a ride · SaathChalo',
    canActivate: [featureGuard('rides.booking')],
    loadComponent: () => import('./features/rides/search-page').then((m) => m.SearchPage),
  },
  {
    path: 'publish',
    title: 'Offer a ride · SaathChalo',
    canActivate: [featureGuard('rides.publish'), profileCompleteGuard],
    loadComponent: () => import('./features/rides/publish-page').then((m) => m.PublishPage),
  },
  {
    path: 'rides/:id',
    title: 'Ride · SaathChalo',
    canActivate: [featureGuard('rides')],
    loadComponent: () => import('./features/rides/ride-page').then((m) => m.RidePage),
  },
  {
    path: 'my-rides',
    title: 'My rides · SaathChalo',
    canActivate: [authGuard, featureGuard('rides')],
    loadComponent: () => import('./features/rides/my-rides-page').then((m) => m.MyRidesPage),
  },
  {
    path: 'admin',
    title: 'Staff console · SaathChalo',
    canActivate: [authGuard],
    loadChildren: () => import('./admin/admin.routes').then((m) => m.ADMIN_ROUTES),
  },
  // "Coming soon" pages for areas the owner has switched on ahead of launch.
  ...FEATURE_PAGES.map((p) => ({
    path: p.path.slice(1),
    title: `${p.label} · SaathChalo`,
    canActivate: [featureGuard(p.key)],
    data: { feature: p.key },
    loadComponent: () => import('./features/coming-soon-page').then((m) => m.ComingSoonPage),
  })),
  {
    path: '**',
    title: 'Page not found · SaathChalo',
    loadComponent: () => import('./features/not-found-page').then((m) => m.NotFoundPage),
  },
];
