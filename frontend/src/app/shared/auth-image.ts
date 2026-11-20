import { HttpClient } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, effect, inject, input, OnDestroy, signal } from '@angular/core';
import { firstValueFrom } from 'rxjs';

/**
 * A private image (pickup photos, documents): fetched with the login token, since a plain <img> can't send it, and
 * shown from memory. Click to open it full size. Give the element its size (e.g. class="h-20 w-28"); the photo fills
 * that box, cropped (`cover`) or whole (`contain`, for documents that must be read).
 */
@Component({
  selector: 'app-auth-img',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'block overflow-hidden rounded-xl' },
  template: `
    @if (url(); as u) {
      <a [href]="u" target="_blank" rel="noopener" class="block h-full w-full bg-slate-100 dark:bg-slate-800" [attr.aria-label]="alt() ? alt() + ' (opens full size)' : null">
        <img [src]="u" [alt]="alt()" class="h-full w-full" [class.object-cover]="fit() === 'cover'" [class.object-contain]="fit() === 'contain'" />
      </a>
    } @else if (failed()) {
      <div class="flex h-full w-full items-center justify-center bg-slate-100 p-2 text-center text-xs text-slate-500 dark:bg-slate-800 dark:text-slate-400">Photo unavailable</div>
    } @else {
      <div class="skeleton h-full w-full"></div>
    }
  `,
})
export class AuthImage implements OnDestroy {
  private readonly http = inject(HttpClient);

  readonly src = input.required<string>();
  readonly alt = input('');
  readonly fit = input<'cover' | 'contain'>('cover');

  protected readonly url = signal<string | null>(null);
  protected readonly failed = signal(false);

  constructor() {
    effect(() => {
      const src = this.src();
      void this.load(src);
    });
  }

  ngOnDestroy(): void {
    const u = this.url();
    if (u) URL.revokeObjectURL(u);
  }

  private async load(src: string): Promise<void> {
    try {
      const blob = await firstValueFrom(this.http.get(src, { responseType: 'blob' }));
      const old = this.url();
      if (old) URL.revokeObjectURL(old);
      this.url.set(URL.createObjectURL(blob));
    } catch {
      this.failed.set(true);
    }
  }
}
