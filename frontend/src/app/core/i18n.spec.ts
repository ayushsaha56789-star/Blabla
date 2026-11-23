import { TestBed } from '@angular/core/testing';
import { HINDI } from './i18n-hi';
import { I18nService, toHindi } from './i18n.service';

describe('Hindi (beta)', () => {
  afterEach(() => {
    localStorage.removeItem('sc.lang');
    document.body.innerHTML = '';
  });

  it('translates known texts, ignoring extra spaces, and leaves the rest alone', () => {
    expect(toHindi('Find a ride')).toBe('राइड खोजें');
    expect(toHindi('  Find   a ride \n')).toBe('राइड खोजें');
    expect(toHindi('Delhi')).toBeNull();
    expect(toHindi('')).toBeNull();
  });

  it('fills in numbers and names', () => {
    expect(toHindi('3 seats left')).toBe('3 सीट बाकी');
    expect(toHindi('Priya asked for 2 seats')).toBe('Priya ने 2 सीट माँगी');
    expect(toHindi('How was your trip with Ravi?')).toBe('Ravi के साथ आपका सफ़र कैसा रहा?');
  });

  it('never translates into English or leaves a key empty', () => {
    for (const [en, hi] of Object.entries(HINDI)) {
      expect(hi.trim().length, en).toBeGreaterThan(0);
      expect(hi, en).not.toBe(en);
    }
  });

  it('switches the page to Hindi and back, but never members\' own words', async () => {
    document.body.innerHTML = `<nav><a> Find a ride </a><input placeholder="City or town"></nav>
      <p translate="no">Find a ride</p><p>Delhi</p>`;
    const i18n = TestBed.inject(I18nService);
    i18n.lang.set('hi');
    TestBed.tick();
    expect(document.querySelector('a')!.textContent).toBe(' राइड खोजें ');
    expect(document.querySelector('input')!.getAttribute('placeholder')).toBe('शहर या कस्बा');
    expect(document.querySelector('p[translate]')!.textContent).toBe('Find a ride');
    expect(document.documentElement.lang).toBe('hi');

    // Text added later (a new page) is translated too.
    const later = document.createElement('button');
    later.textContent = 'Search';
    document.body.append(later);
    await new Promise((r) => setTimeout(r));
    expect(later.textContent).toBe('खोजें');

    i18n.lang.set('en');
    TestBed.tick();
    expect(document.querySelector('a')!.textContent).toBe(' Find a ride ');
    expect(document.querySelector('input')!.getAttribute('placeholder')).toBe('City or town');
    expect(later.textContent).toBe('Search');
    expect(localStorage.getItem('sc.lang')).toBe('en');
  });
});
