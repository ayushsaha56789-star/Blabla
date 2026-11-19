import { effect, Injectable, signal } from '@angular/core';
import { storageGet, storageSet } from './util';

/** Light = white theme, dark = black theme, system = follow the phone or computer setting. */
export type ThemeMode = 'light' | 'dark' | 'system';
const KEY = 'sc.theme';

@Injectable({ providedIn: 'root' })
export class ThemeService {
  // matchMedia is missing in some non-browser environments (e.g. unit tests).
  private readonly media: MediaQueryList | null =
    typeof window.matchMedia === 'function' ? window.matchMedia('(prefers-color-scheme: dark)') : null;
  readonly mode = signal<ThemeMode>(this.saved());
  readonly isDark = signal(document.documentElement.classList.contains('dark'));

  constructor() {
    effect(() => {
      storageSet(KEY, this.mode());
      this.apply();
    });
    this.media?.addEventListener('change', () => this.apply());
  }

  set(mode: ThemeMode): void {
    this.mode.set(mode);
  }

  toggle(): void {
    this.mode.set(this.isDark() ? 'light' : 'dark');
  }

  private apply(): void {
    const dark = this.mode() === 'dark' || (this.mode() === 'system' && !!this.media?.matches);
    document.documentElement.classList.toggle('dark', dark);
    document.querySelector('meta[name="theme-color"]')?.setAttribute('content', dark ? '#0b0b0b' : '#ffffff');
    this.isDark.set(dark);
  }

  private saved(): ThemeMode {
    const v = storageGet(KEY);
    return v === 'light' || v === 'dark' ? v : 'system';
  }
}
