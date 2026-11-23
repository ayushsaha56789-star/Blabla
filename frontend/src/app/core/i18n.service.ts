import { effect, Injectable, signal, untracked } from '@angular/core';
import { HINDI, HINDI_PATTERNS } from './i18n-hi';
import { storageGet, storageSet } from './util';

export type Lang = 'en' | 'hi';

const ATTRIBUTES = ['placeholder', 'aria-label', 'title', 'alt'];
/** Never translated: code, form fields, and anything marked translate="no" (members' own words, names, places). */
const SKIP = 'script, style, textarea, code, pre, [translate="no"], [contenteditable="true"]';

/** Hindi for an English interface text, or null when there's no translation yet. */
export function toHindi(english: string): string | null {
  const key = english.replace(/\s+/g, ' ').trim();
  if (!key) return null;
  const exact = HINDI[key];
  if (exact) return exact;
  for (const [pattern, replace] of HINDI_PATTERNS) {
    const m = pattern.exec(key);
    if (m) return replace(m);
  }
  return null;
}

/**
 * English or Hindi (beta). In Hindi the interface text on the page is swapped for its translation as Angular renders
 * it, and restored when the member switches back; text without a translation stays in English. The choice is kept in
 * this browser only.
 */
@Injectable({ providedIn: 'root' })
export class I18nService {
  readonly lang = signal<Lang>(initialLang());

  private observer: MutationObserver | null = null;
  /** The English we replaced, and the Hindi we wrote (so our own writes aren't translated again). */
  private readonly englishText = new WeakMap<Text, string>();
  private readonly writtenText = new WeakMap<Text, string>();
  private readonly englishAttrs = new WeakMap<Element, Map<string, string>>();
  private readonly writtenAttrs = new WeakMap<Element, Map<string, string>>();
  private readonly touched = new Set<WeakRef<Node>>();

  constructor() {
    effect(() => {
      const lang = this.lang();
      document.documentElement.lang = lang;
      storageSet('sc.lang', lang);
      untracked(() => (lang === 'hi' ? this.start() : this.stop()));
    });
  }

  toggle(): void {
    this.lang.set(this.lang() === 'hi' ? 'en' : 'hi');
  }

  /** For text made in code (toasts, confirmations): Hindi when chosen and known. */
  t(english: string): string {
    return this.lang() === 'hi' ? (toHindi(english) ?? english) : english;
  }

  private start(): void {
    if (this.observer || typeof MutationObserver === 'undefined') return;
    this.walk(document.body);
    this.observer = new MutationObserver((records) => {
      for (const r of records) {
        if (r.type === 'characterData') this.text(r.target as Text);
        else if (r.type === 'attributes' && r.attributeName) this.attribute(r.target as Element, r.attributeName);
        else r.addedNodes.forEach((n) => this.walk(n));
      }
    });
    this.observer.observe(document.body, {
      subtree: true, childList: true, characterData: true, attributes: true, attributeFilter: ATTRIBUTES,
    });
  }

  private stop(): void {
    if (!this.observer) return;
    this.observer.disconnect();
    this.observer = null;
    for (const ref of this.touched) {
      const node = ref.deref();
      if (node instanceof Text) {
        if (this.writtenText.get(node) === node.data) node.data = this.englishText.get(node) ?? node.data;
      } else if (node instanceof Element) {
        const english = this.englishAttrs.get(node);
        const written = this.writtenAttrs.get(node);
        english?.forEach((value, name) => {
          if (written?.get(name) === node.getAttribute(name)) node.setAttribute(name, value);
        });
      }
    }
    this.touched.clear();
  }

  private walk(root: Node): void {
    if (root instanceof Text) {
      this.text(root);
      return;
    }
    if (!(root instanceof Element) || root.closest(SKIP)) return;
    const texts = document.createTreeWalker(root, NodeFilter.SHOW_TEXT);
    for (let n = texts.nextNode(); n; n = texts.nextNode()) this.text(n as Text);
    for (const name of ATTRIBUTES) {
      if (root.hasAttribute(name)) this.attribute(root, name);
      root.querySelectorAll(`[${name}]`).forEach((el) => this.attribute(el, name));
    }
  }

  private text(node: Text): void {
    const data = node.data;
    if (!data.trim() || this.writtenText.get(node) === data || node.parentElement?.closest(SKIP)) return;
    const hindi = toHindi(data);
    if (!hindi) return;
    const out = data.replace(data.trim(), hindi);
    this.englishText.set(node, data);
    this.writtenText.set(node, out);
    this.remember(node);
    node.data = out;
  }

  /** Keeps track of what we changed, forgetting nodes that are gone so a long visit doesn't pile them up. */
  private remember(node: Node): void {
    this.touched.add(new WeakRef(node));
    if (this.touched.size > 3000) {
      for (const ref of this.touched) if (!ref.deref()?.isConnected) this.touched.delete(ref);
    }
  }

  private attribute(el: Element, name: string): void {
    const value = el.getAttribute(name);
    if (!value || this.writtenAttrs.get(el)?.get(name) === value || el.closest(SKIP)) return;
    const hindi = toHindi(value);
    if (!hindi) return;
    if (!this.englishAttrs.has(el)) this.englishAttrs.set(el, new Map());
    if (!this.writtenAttrs.has(el)) this.writtenAttrs.set(el, new Map());
    this.englishAttrs.get(el)!.set(name, value);
    this.writtenAttrs.get(el)!.set(name, hindi);
    this.remember(el);
    el.setAttribute(name, hindi);
  }
}

function initialLang(): Lang {
  const saved = storageGet('sc.lang');
  if (saved === 'hi' || saved === 'en') return saved;
  return typeof navigator !== 'undefined' && navigator.language?.toLowerCase().startsWith('hi') ? 'hi' : 'en';
}
