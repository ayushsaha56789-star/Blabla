import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';

/** Circle as path data. */
const c = (cx: number, cy: number, r: number) => `M${cx - r} ${cy}a${r} ${r} 0 1 0 ${2 * r} 0a${r} ${r} 0 1 0 ${-2 * r} 0`;

/** 24×24 line icons, drawn for SaathChalo. Each entry is a list of SVG path strings. */
const ICONS = {
  search: [c(11, 11, 7), 'm20 20-3.5-3.5'],
  pin: ['M12 21s-7-6.2-7-11.5a7 7 0 0 1 14 0C19 14.8 12 21 12 21z', c(12, 9.5, 2.5)],
  calendar: ['M6 5h12a2.5 2.5 0 0 1 2.5 2.5v10.5a2.5 2.5 0 0 1-2.5 2.5H6a2.5 2.5 0 0 1-2.5-2.5V7.5A2.5 2.5 0 0 1 6 5z', 'M3.5 10h17M8 3v4M16 3v4'],
  user: [c(12, 8, 4), 'M4 20.5c1.5-4 4.5-6 8-6s6.5 2 8 6'],
  users: [c(9, 8, 3.5), 'M2.5 20c1.2-3.6 3.6-5.5 6.5-5.5s5.3 1.9 6.5 5.5', 'M15.5 4.8a3.5 3.5 0 0 1 0 6.4M17.5 14.8c1.9.7 3.2 2.4 4 5.2'],
  car: ['M5 17H3.8a.8.8 0 0 1-.8-.8v-3.4a2 2 0 0 1 .6-1.4L6 9l1.6-3.2A1.5 1.5 0 0 1 9 5h6a1.5 1.5 0 0 1 1.4.8L18 9l2.4 2.4a2 2 0 0 1 .6 1.4v3.4a.8.8 0 0 1-.8.8H19', 'M6 9h12M9.5 17h5', c(7.2, 17, 2), c(16.8, 17, 2)],
  shield: ['M12 3l7.5 3v5.5c0 4.5-3.2 8.2-7.5 9.5-4.3-1.3-7.5-5-7.5-9.5V6z', 'm8.8 12 2.2 2.2 4.3-4.4'],
  rupee: ['M7 4.5h10M7 9h10M7 4.5h3.5a4.5 4.5 0 0 1 0 9H7l7.5 7'],
  leaf: ['M5 19c0-8 5.5-13 15-14-1 9.5-6 15-14 15', 'M5 19c3-4 6-6.5 9.5-8.5'],
  moon: ['M20 14.5A8 8 0 1 1 9.5 4a6.5 6.5 0 0 0 10.5 10.5z'],
  sun: [c(12, 12, 4), 'M12 2.5v2M12 19.5v2M4.6 4.6 6 6M18 18l1.4 1.4M2.5 12h2M19.5 12h2M4.6 19.4 6 18M18 6l1.4-1.4'],
  menu: ['M4 7h16M4 12h16M4 17h16'],
  close: ['M6 6l12 12M18 6 6 18'],
  arrowRight: ['M5 12h14M13 6l6 6-6 6'],
  arrowLeft: ['M19 12H5M11 6l-6 6 6 6'],
  swap: ['M7 4v16M3.5 7.5 7 4l3.5 3.5M17 20V4M13.5 16.5 17 20l3.5-3.5'],
  camera: ['M4 8.5A2.5 2.5 0 0 1 6.5 6h1.8l1.5-2h4.4l1.5 2h1.8A2.5 2.5 0 0 1 20 8.5v9a2.5 2.5 0 0 1-2.5 2.5h-11A2.5 2.5 0 0 1 4 17.5z', c(12, 13, 3.5)],
  check: ['m5 12.5 4.5 4.5L19 7.5'],
  checkCircle: [c(12, 12, 8.5), 'm8.5 12.2 2.4 2.4 4.6-4.8'],
  phone: ['M5 4h3.5l1.8 4.5-2.3 1.4a11 11 0 0 0 6.1 6.1l1.4-2.3L20 15.5V19a1.5 1.5 0 0 1-1.6 1.5C10.6 20 4 13.4 3.5 5.6A1.5 1.5 0 0 1 5 4z'],
  star: ['m12 3.5 2.6 5.3 5.9.9-4.3 4.1 1 5.8L12 16.9l-5.2 2.7 1-5.8-4.3-4.1 5.9-.9z'],
  chevronDown: ['m6 9 6 6 6-6'],
  logout: ['M15 4h3.5A1.5 1.5 0 0 1 20 5.5v13a1.5 1.5 0 0 1-1.5 1.5H15M10 16l-4-4 4-4M6 12h10'],
  music: ['M9 18V5.5l11-2V16', c(6.5, 18, 2.5), c(17.5, 16, 2.5)],
  chat: ['M4 5.5A2.5 2.5 0 0 1 6.5 3h11A2.5 2.5 0 0 1 20 5.5v8a2.5 2.5 0 0 1-2.5 2.5H10L6 20v-4h.5A2.5 2.5 0 0 1 4 13.5z'],
  paw: [c(7, 9, 1.8), c(12, 6.5, 1.8), c(17, 9, 1.8), 'M12 12c-3 0-5.5 3.5-5.5 5.5 0 1.5 1.2 2.5 2.7 2.5 1.2 0 1.8-.6 2.8-.6s1.6.6 2.8.6c1.5 0 2.7-1 2.7-2.5 0-2-2.5-5.5-5.5-5.5z'],
  smoke: ['M3 15h14v3H3zM20 15v3M17.5 4.5c0 2 2 2 2 4s-2 2-2 4'],
  woman: [c(12, 9, 5), 'M12 14v7M9 18h6'],
  clock: [c(12, 12, 8.5), 'M12 7.5V12l3 2'],
  edit: ['M4 20h4L19 9a2.8 2.8 0 0 0-4-4L4 16z', 'm13.5 6.5 4 4'],
  trash: ['M4 7h16M9 7V4.5h6V7M6.5 7l1 13h9l1-13'],
  info: [c(12, 12, 8.5), 'M12 11v5M12 8h.01'],
  plus: ['M12 5v14M5 12h14'],
  minus: ['M5 12h14'],
  plusCircle: [c(12, 12, 8.5), 'M12 8v8M8 12h8'],
  sparkles: ['M12 3.5l1.6 4.4 4.4 1.6-4.4 1.6L12 15.5l-1.6-4.4L6 9.5l4.4-1.6zM18.5 15l.8 2 2 .8-2 .8-.8 2-.8-2-2-.8 2-.8z'],
  route: [c(6, 18, 2.5), c(18, 6, 2.5), 'M8.5 18H15a3 3 0 0 0 0-6H9a3 3 0 0 1 0-6h6.5'],
  heart: ['M12 20s-7.5-4.6-7.5-10.2A4.3 4.3 0 0 1 12 7.2a4.3 4.3 0 0 1 7.5 2.6C19.5 15.4 12 20 12 20z'],
} as const satisfies Record<string, readonly string[]>;

export type IconName = keyof typeof ICONS;

@Component({
  selector: 'app-icon',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'inline-flex shrink-0', '[style.width.px]': 'size()', '[style.height.px]': 'size()' },
  template: `
    <svg viewBox="0 0 24 24" class="h-full w-full" fill="none" stroke="currentColor"
         [attr.stroke-width]="stroke()" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
      @for (d of paths(); track $index) {
        <path [attr.d]="d" />
      }
    </svg>
  `,
})
export class Icon {
  readonly name = input.required<IconName>();
  readonly size = input(20);
  readonly stroke = input(1.8);
  protected readonly paths = computed(() => ICONS[this.name()]);
}
