import { HttpErrorResponse } from '@angular/common/http';
import { ApiProblem } from './api.models';

const GENERIC = 'Something went wrong. Please try again.';

/** Turns any error into a message that is safe and friendly to show. */
export function problemOf(error: unknown): ApiProblem {
  if (error instanceof HttpErrorResponse) {
    if (error.status === 0) {
      return { status: 0, detail: "We can't reach SaathChalo right now. Check your internet connection and try again." };
    }
    const body = error.error;
    if (body && typeof body === 'object' && typeof body.detail === 'string' && error.status < 500) {
      return {
        status: error.status,
        detail: body.detail,
        code: body.code,
        errors: body.errors,
        reason: body.reason,
        until: body.until,
        attemptsLeft: body.attemptsLeft,
      };
    }
    if (error.status === 503 && body?.code === 'maintenance') {
      return { status: 503, code: 'maintenance', detail: body.detail };
    }
    if (error.status >= 500) {
      return { status: error.status, detail: 'Something went wrong on our side. Please try again in a moment.' };
    }
    return { status: error.status, detail: GENERIC };
  }
  return { status: -1, detail: GENERIC };
}
