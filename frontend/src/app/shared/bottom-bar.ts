import { DestroyRef, Directive, DOCUMENT, inject } from '@angular/core';

let shown = 0;

/**
 * For bars fixed to the bottom of the screen on phones (step buttons, a save bar): while one is shown, the page gets
 * that much extra room at the end (see `body.has-bottom-bar` in styles.css), so the bar never covers the last content
 * or the footer.
 */
@Directive({ selector: '[appBottomBar]' })
export class BottomBar {
  constructor() {
    const body = inject(DOCUMENT).body;
    shown++;
    body.classList.add('has-bottom-bar');
    inject(DestroyRef).onDestroy(() => {
      shown = Math.max(0, shown - 1);
      if (!shown) body.classList.remove('has-bottom-bar');
    });
  }
}
