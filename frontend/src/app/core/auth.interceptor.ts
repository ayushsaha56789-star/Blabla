import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { catchError, from, switchMap, throwError } from 'rxjs';
import { AuthService } from './auth.service';
import { problemOf } from './errors';
import { FeaturesService } from './features.service';

/** Endpoints that work with the refresh cookie only, never an access token. */
const COOKIE_ONLY = ['/api/auth/otp/', '/api/auth/refresh', '/api/auth/logout'];

/** Endpoints that need the staff console token (added by adminInterceptor instead). */
export const CONSOLE_ONLY = ['/api/admin/', '/api/auth/staff/logout', '/api/auth/staff/recovery-codes'];

/** Adds the member's access token to API calls, and transparently refreshes it once when it has expired. */
export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const url = req.url;
  if (!url.startsWith('/api/') || COOKIE_ONLY.some((p) => url.startsWith(p)) || CONSOLE_ONLY.some((p) => url.startsWith(p))) {
    return next(req);
  }
  const auth = inject(AuthService);
  const token = auth.accessToken();
  const withToken = (t: string | null) => (t ? req.clone({ setHeaders: { Authorization: `Bearer ${t}` } }) : req);

  return next(withToken(token)).pipe(
    catchError((err: unknown) => {
      if (err instanceof HttpErrorResponse && err.status === 401 && token) {
        return from(auth.refresh()).pipe(
          switchMap((ok) => (ok ? next(withToken(auth.accessToken())) : throwError(() => err))),
        );
      }
      return throwError(() => err);
    }),
  );
};

/** Reacts to answers that change the whole app: a suspended account, or maintenance mode. */
export const statusInterceptor: HttpInterceptorFn = (req, next) => {
  const auth = inject(AuthService);
  const features = inject(FeaturesService);
  return next(req).pipe(
    catchError((err: unknown) => {
      if (err instanceof HttpErrorResponse) {
        const p = problemOf(err);
        if (err.status === 403 && p.code === 'account_suspended' && !req.url.startsWith('/api/auth/otp/')) {
          auth.markSuspended(p.reason ?? null, p.until ?? null);
        } else if (err.status === 503 && p.code === 'maintenance') {
          features.setMaintenance(true);
        }
      }
      return throwError(() => err);
    }),
  );
};
