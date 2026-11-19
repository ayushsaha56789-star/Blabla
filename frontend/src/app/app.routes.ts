import { Routes } from '@angular/router';
import { authGuard, guestGuard, profileCompleteGuard } from './core/auth.guards';

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
    loadComponent: () => import('./features/rides/search-page').then((m) => m.SearchPage),
  },
  {
    path: 'publish',
    title: 'Offer a ride · SaathChalo',
    canActivate: [profileCompleteGuard],
    loadComponent: () => import('./features/rides/publish-page').then((m) => m.PublishPage),
  },
  {
    path: '**',
    title: 'Page not found · SaathChalo',
    loadComponent: () => import('./features/not-found-page').then((m) => m.NotFoundPage),
  },
];
