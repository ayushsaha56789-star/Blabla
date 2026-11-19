import { HttpErrorResponse } from '@angular/common/http';
import { problemOf } from './errors';
import { formatIndianMobile, isValidIndianMobile, safeReturnUrl, todayIso } from './util';

describe('phone helpers', () => {
  it('formats as the user types', () => {
    expect(formatIndianMobile('98765')).toBe('98765');
    expect(formatIndianMobile('9876543210')).toBe('98765 43210');
    expect(formatIndianMobile('98765-432109999')).toBe('98765 43210');
  });

  it('accepts only Indian mobile numbers', () => {
    expect(isValidIndianMobile('98765 43210')).toBe(true);
    expect(isValidIndianMobile('58765 43210')).toBe(false);
    expect(isValidIndianMobile('98765')).toBe(false);
  });
});

describe('safeReturnUrl', () => {
  it('keeps in-app paths', () => {
    expect(safeReturnUrl('/profile?tab=1')).toBe('/profile?tab=1');
  });

  it('refuses other sites and junk', () => {
    expect(safeReturnUrl('https://evil.example')).toBe('/');
    expect(safeReturnUrl('//evil.example')).toBe('/');
    expect(safeReturnUrl('/\\evil.example')).toBe('/');
    expect(safeReturnUrl(undefined)).toBe('/');
  });
});

describe('todayIso', () => {
  it('uses the local calendar date', () => {
    expect(todayIso(new Date(2026, 10, 19, 23, 30))).toBe('2026-11-19');
  });
});

describe('problemOf', () => {
  it('passes through friendly API messages', () => {
    const err = new HttpErrorResponse({ status: 400, error: { detail: 'Please wait 20 seconds.', code: 'otp_cooldown' } });
    expect(problemOf(err)).toEqual({ status: 400, detail: 'Please wait 20 seconds.', code: 'otp_cooldown', errors: undefined });
  });

  it('explains network failures', () => {
    expect(problemOf(new HttpErrorResponse({ status: 0 })).detail).toContain('internet connection');
  });

  it('hides server internals', () => {
    const err = new HttpErrorResponse({ status: 500, error: { detail: 'NullPointerException at Foo.java:12' } });
    expect(problemOf(err).detail).not.toContain('NullPointer');
  });
});

describe('PhonePipe', () => {
  it('groups Indian numbers the familiar way', async () => {
    const { PhonePipe } = await import('../shared/phone.pipe');
    expect(new PhonePipe().transform('+919876543210')).toBe('+91 98765 43210');
    expect(new PhonePipe().transform(null)).toBe('');
  });
});
