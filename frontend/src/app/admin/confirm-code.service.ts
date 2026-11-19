import { Injectable, signal } from '@angular/core';

/** Thrown when the staff member closes the "confirm with your code" dialog instead of entering a code. */
export class ConfirmationCancelled extends Error {
  constructor() {
    super('Confirmation cancelled');
  }
}

/**
 * Asks for a fresh 2FA code before a sensitive action (adding staff, critical switches, new recovery codes).
 * The dialog itself is rendered by the console shell; this service only holds its state.
 */
@Injectable({ providedIn: 'root' })
export class ConfirmCodeService {
  readonly open = signal(false);
  readonly error = signal<string | null>(null);
  private resolve: ((code: string | null) => void) | null = null;

  request(error: string | null): Promise<string | null> {
    this.resolve?.(null);
    this.error.set(error);
    this.open.set(true);
    return new Promise((resolve) => (this.resolve = resolve));
  }

  submit(code: string): void {
    this.finish(code);
  }

  cancel(): void {
    this.finish(null);
  }

  private finish(code: string | null): void {
    const resolve = this.resolve;
    this.resolve = null;
    this.open.set(false);
    resolve?.(code);
  }
}
