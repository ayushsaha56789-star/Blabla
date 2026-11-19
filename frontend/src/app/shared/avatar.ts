import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';

/** A round profile photo, or coloured initials when there is no photo. */
@Component({
  selector: 'app-avatar',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: {
    class: 'relative inline-flex shrink-0 overflow-hidden rounded-full ring-2 ring-white dark:ring-slate-900',
    '[style.width.px]': 'size()',
    '[style.height.px]': 'size()',
  },
  template: `
    @if (url()) {
      <img [src]="url()" [alt]="name() + ' profile photo'" class="h-full w-full object-cover" loading="lazy" />
    } @else {
      <span class="flex h-full w-full items-center justify-center bg-linear-to-br font-bold text-white"
            [class]="gradient()" [style.font-size.px]="size() * 0.38" aria-hidden="true">{{ initials() }}</span>
    }
  `,
})
export class Avatar {
  readonly url = input<string | null | undefined>(null);
  readonly name = input('');
  readonly size = input(40);

  protected readonly initials = computed(() => {
    const parts = this.name().trim().split(/\s+/).filter(Boolean);
    const letters = parts.length > 1 ? parts[0][0] + parts[1][0] : (parts[0]?.slice(0, 1) ?? '');
    return letters.toUpperCase() || '🙂';
  });

  /** Same name, same colour — so people are easy to recognise. */
  protected readonly gradient = computed(() => {
    const palettes = [
      'from-brand-400 to-brand-700',
      'from-accent-400 to-accent-600',
      'from-sky-400 to-indigo-600',
      'from-fuchsia-400 to-purple-600',
      'from-rose-400 to-pink-600',
      'from-teal-400 to-cyan-600',
    ];
    let hash = 0;
    for (const ch of this.name()) hash = (hash * 31 + ch.charCodeAt(0)) | 0;
    return palettes[Math.abs(hash) % palettes.length];
  });
}
