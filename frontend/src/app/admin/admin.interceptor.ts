import { HttpErrorResponse, HttpEvent, HttpInterceptorFn, HttpRequest, HttpHandlerFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { Observable, catchError, from, switchMap, throwError } from 'rxjs';
import { CONSOLE_ONLY } from '../core/auth.interceptor';
import { problemOf } from '../core/errors';
import { ConfirmationCancelled, ConfirmCodeService } from './confirm-code.service';
import { ConsoleSessionService } from './console-session.service';

const MAX_CODE_TRIES = 3;

/**
 * For console endpoints: adds the console token, ends the console session when the server says it is over (401),
 * and when the server asks for a fresh 2FA code (403 confirmation_required / confirmation_invalid) shows the code
 * dialog and repeats the request with the code — so pages don't need to know which actions are sensitive.
 */
export const adminInterceptor: HttpInterceptorFn = (req, next) => {
  if (!CONSOLE_ONLY.some((p) => req.url.startsWith(p))) {
    return next(req);
  }
  const session = inject(ConsoleSessionService);
  const confirm = inject(ConfirmCodeService);
  return attempt(req, next, session, confirm, null, 0);
};

function attempt(
  req: HttpRequest<unknown>,
  next: HttpHandlerFn,
  session: ConsoleSessionService,
  confirm: ConfirmCodeService,
  code: string | null,
  tries: number,
): Observable<HttpEvent<unknown>> {
  const token = session.consoleToken();
  let request = token ? req.clone({ setHeaders: { Authorization: `Bearer ${token}` } }) : req;
  if (code) request = request.clone({ setHeaders: { 'X-Confirm-Code': code } });

  return next(request).pipe(
    catchError((err: unknown) => {
      if (err instanceof HttpErrorResponse) {
        if (err.status === 401 && token) {
          session.end(true);
        }
        const p = problemOf(err);
        if (err.status === 403 && (p.code === 'confirmation_required' || p.code === 'confirmation_invalid') && tries < MAX_CODE_TRIES) {
          return from(confirm.request(p.code === 'confirmation_invalid' ? p.detail : null)).pipe(
            switchMap((entered) =>
              entered ? attempt(req, next, session, confirm, entered, tries + 1) : throwError(() => new ConfirmationCancelled()),
            ),
          );
        }
      }
      return throwError(() => err);
    }),
  );
}
