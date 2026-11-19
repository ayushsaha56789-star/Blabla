import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from './auth.service';

export const authGuard: CanActivateFn = (_route, state) => {
  const auth = inject(AuthService);
  return auth.isLoggedIn() || inject(Router).createUrlTree(['/login'], { queryParams: { returnUrl: state.url } });
};

/** Logged in *and* has told us their name and gender (needed to offer or book rides). */
export const profileCompleteGuard: CanActivateFn = (_route, state) => {
  const auth = inject(AuthService);
  const router = inject(Router);
  if (!auth.isLoggedIn()) {
    return router.createUrlTree(['/login'], { queryParams: { returnUrl: state.url } });
  }
  return auth.user()?.profileComplete || router.createUrlTree(['/onboarding'], { queryParams: { returnUrl: state.url } });
};

/** The login page makes no sense when already logged in. */
export const guestGuard: CanActivateFn = () => {
  return !inject(AuthService).isLoggedIn() || inject(Router).createUrlTree(['/']);
};
