import { fareLines, tripLength, tripStatus } from './drivers-api';

describe('hiring a driver on screen', () => {
  it('says what happens next, from each side', () => {
    const base = { payOnline: false, cancelledBy: null } as const;
    expect(tripStatus({ ...base, status: 'REQUESTED', viewer: 'DRIVER' }).text).toBe('Waiting for your answer');
    expect(tripStatus({ ...base, status: 'REQUESTED', viewer: 'CUSTOMER' }).text).toBe('Waiting for the driver');
    expect(tripStatus({ ...base, payOnline: true, status: 'ACCEPTED', viewer: 'CUSTOMER' }).text).toBe('Accepted: pay to confirm');
    expect(tripStatus({ ...base, status: 'ACCEPTED', viewer: 'CUSTOMER' }).tone).toBe('success');
    expect(tripStatus({ ...base, status: 'ON_THE_WAY', viewer: 'CUSTOMER' }).text).toBe('Driver on the way');
    expect(tripStatus({ ...base, status: 'CANCELLED', viewer: 'CUSTOMER', cancelledBy: 'DRIVER' }).text).toBe('Cancelled by the driver');
    expect(tripStatus({ ...base, status: 'CANCELLED', viewer: 'DRIVER', cancelledBy: 'CUSTOMER' }).text).toBe('Cancelled by the member');
  });

  it('writes trip lengths and price lines people can check', () => {
    expect(tripLength('LOCAL', 1)).toBe('1 hour');
    expect(tripLength('LOCAL', 6)).toBe('6 hours');
    expect(tripLength('OUTSTATION', 3)).toBe('3 days');
    const rates = { hourPrice: 150, dayPrice: 1000, outstationPrice: 1500, allowance: 300, nightCharge: 200 };
    expect(fareLines({ kind: 'LOCAL', units: 4, base: 600, dayPriceApplied: false, night: 200, allowance: 0 }, rates)).toEqual([
      { label: '4 h × ₹150', amount: 600 },
      { label: 'Night charge (10 pm – 6 am)', amount: 200 },
    ]);
    expect(fareLines({ kind: 'LOCAL', units: 10, base: 1000, dayPriceApplied: true, night: 0, allowance: 0 }, rates)[0].label)
      .toBe('10 hours (capped at the day price)');
    expect(fareLines({ kind: 'OUTSTATION', units: 2, base: 3000, dayPriceApplied: false, night: 0, allowance: 600 }, rates)).toEqual([
      { label: '2 days × ₹1,500', amount: 3000 },
      { label: 'Food and stay, 2 × ₹300', amount: 600 },
    ]);
  });
});
