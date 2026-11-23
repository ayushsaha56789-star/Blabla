import { OverlayContainer } from '@angular/cdk/overlay';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { PlaceInput } from '../shared/place-input';
import { addDays, bookingStatus, duration, rupees, seatsLeftFor, stretchPrice, whatsappLink } from './rides-format';
import { Place } from './rides.models';

describe('ride formatting', () => {
  it('writes rupees the Indian way', () => {
    expect(rupees(590)).toBe('₹590');
    expect(rupees(1040)).toBe('₹1,040');
    expect(rupees(125000)).toBe('₹1,25,000');
  });

  it('writes trip lengths in hours and minutes', () => {
    expect(duration(345)).toBe('5 h 45 min');
    expect(duration(180)).toBe('3 h');
    expect(duration(40)).toBe('40 min');
  });

  it('counts free seats on the busiest part of a stretch', () => {
    expect(seatsLeftFor([2, 0, 1], 0, 1)).toBe(2);
    expect(seatsLeftFor([2, 0, 1], 0, 3)).toBe(0);
    expect(seatsLeftFor([2, 0, 1], 2, 3)).toBe(1);
  });

  it('prices part of a route like the server does', () => {
    const km = [0, 34, 295];
    expect(stretchPrice(600, km, 0, 2)).toBe(600);
    expect(stretchPrice(600, km, 1, 2)).toBe(530);
    expect(stretchPrice(600, km, 0, 1)).toBe(70);
    expect(stretchPrice(40, [0, 1, 300], 0, 1)).toBe(10);
  });

  it('explains booking states in plain words', () => {
    expect(bookingStatus('REQUESTED').text).toBe('Waiting for the driver');
    expect(bookingStatus('CANCELLED', 'DRIVER').text).toBe('Ride cancelled by the driver');
    expect(bookingStatus('CANCELLED', 'PASSENGER').tone).toBe('muted');
  });

  it('handles dates and WhatsApp links', () => {
    expect(addDays('2026-11-30', 1)).toBe('2026-12-01');
    expect(addDays('2026-03-01', -1)).toBe('2026-02-28');
    expect(whatsappLink('+919876543210')).toBe('https://wa.me/919876543210');
  });
});

describe('place picker', () => {
  const bengaluru: Place = { id: 1277333, name: 'Bengaluru', state: 'Karnataka', lat: 12.97, lng: 77.59, population: 8495492 };
  const rural: Place = { id: 1, name: 'Bangalore Rural', state: 'Karnataka', lat: 13.2, lng: 77.7, population: 9000 };
  let backend: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    backend = TestBed.inject(HttpTestingController);
  });

  afterEach(() => backend.verify());

  async function typeInto(input: HTMLInputElement, text: string): Promise<void> {
    input.value = text;
    input.dispatchEvent(new Event('input'));
    await new Promise((r) => setTimeout(r, 200));
  }

  it('suggests towns as you type and picks one with the keyboard', async () => {
    const fixture = TestBed.createComponent(PlaceInput);
    const picker = fixture.componentInstance;
    const picked: Place[] = [];
    picker.picked.subscribe((p) => picked.push(p));
    await fixture.whenStable();
    const input: HTMLInputElement = fixture.nativeElement.querySelector('input');

    await typeInto(input, 'bang');
    const req = backend.expectOne((r) => r.url === '/api/places' && r.params.get('q') === 'bang');
    req.flush([bengaluru, rural]);
    await new Promise((r) => setTimeout(r));
    await fixture.whenStable();
    // The list floats in the overlay layer above the page (so nothing can cover or clip it), linked to the field.
    const layer = TestBed.inject(OverlayContainer).getContainerElement();
    const options = layer.querySelectorAll('[role="option"]');
    expect(options.length).toBe(2);
    expect(input.getAttribute('aria-expanded')).toBe('true');
    expect(layer.querySelector(`#${input.getAttribute('aria-controls')}`)?.getAttribute('role')).toBe('listbox');
    expect(fixture.nativeElement.querySelector('[role="option"]')).toBeNull();

    input.dispatchEvent(new KeyboardEvent('keydown', { key: 'ArrowDown' }));
    input.dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter' }));
    await fixture.whenStable();
    expect(picker.value()).toEqual(rural);
    expect(picked).toEqual([rural]);
    expect(input.value).toBe('Bangalore Rural');
  });

  it('picks the best match when someone types a name and moves on', async () => {
    const fixture = TestBed.createComponent(PlaceInput);
    await fixture.whenStable();
    const input: HTMLInputElement = fixture.nativeElement.querySelector('input');
    await typeInto(input, 'Bangalore');
    backend.expectOne((r) => r.url === '/api/places').flush([bengaluru]);
    await fixture.whenStable();
    input.dispatchEvent(new Event('blur'));
    await new Promise((r) => setTimeout(r, 200));
    expect(fixture.componentInstance.value()).toEqual(bengaluru);
  });
});
