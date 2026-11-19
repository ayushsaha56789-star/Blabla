import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { catchError, from, switchMap, throwError } from 'rxjs';
import { AuthService } from './auth.service';

/** Adds the access token to API calls, and transparently refreshes it once when it has expired. */
export const authInterceptor: HttpInterceptorFn = (req, next) => {
  if (!req.url.startsWith('/api/') || req.url.startsWith('/api/auth/')) {
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
