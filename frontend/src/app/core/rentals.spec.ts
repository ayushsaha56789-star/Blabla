import { chargesLine, completedLine, fromLocalInput, rentalStatus, toLocalInput } from './rentals-api';

describe('rentals on screen', () => {
  it('says what happens next, from each side', () => {
    const base = { payOnline: true, cancelledBy: null } as const;
    expect(rentalStatus({ ...base, status: 'REQUESTED', viewer: 'OWNER' }).text).toBe('Waiting for your answer');
    expect(rentalStatus({ ...base, status: 'REQUESTED', viewer: 'RENTER' }).text).toBe('Waiting for the owner');
    expect(rentalStatus({ ...base, status: 'ACCEPTED', viewer: 'RENTER' }).text).toBe('Accepted: pay to confirm');
    expect(rentalStatus({ ...base, payOnline: false, status: 'ACCEPTED', viewer: 'RENTER' }).text).toBe('Confirmed: pay at pickup');
    expect(rentalStatus({ ...base, status: 'RETURNED', viewer: 'RENTER' }).tone).toBe('warning');
    expect(rentalStatus({ ...base, status: 'CANCELLED', viewer: 'RENTER', cancelledBy: 'RENTER' }).text).toBe('You cancelled');
    expect(rentalStatus({ ...base, status: 'CANCELLED', viewer: 'OWNER', cancelledBy: 'RENTER' }).text).toBe('Cancelled by the renter');
  });

  it('explains the charges and what happens to the deposit', () => {
    const r = { deposit: 5000, extraCharges: 0, extraKmCharge: 0, extraNote: null, payOnline: false, viewer: 'RENTER' } as const;
    expect(chargesLine(r)).toBe('No charges: the whole deposit comes back.');
    expect(chargesLine({ ...r, extraCharges: 800, extraKmCharge: 500, extraNote: 'Half a tank' }))
      .toBe('The owner keeps ₹800 from the deposit (₹500 for extra km): Half a tank.');
    // Cash deposits go back by hand; online ones as a refund.
    expect(completedLine(r)).toBe('Completed. The owner gives your ₹5,000 deposit back.');
    expect(completedLine({ ...r, viewer: 'OWNER' })).toBe('Completed. Please give the renter\'s ₹5,000 deposit back.');
    expect(completedLine({ ...r, extraCharges: 300, viewer: 'OWNER' })).toBe('Completed. Keep ₹300 from the deposit and give ₹4,700 back to the renter.');
    expect(completedLine({ ...r, payOnline: true, extraCharges: 300 })).toBe('Completed. The owner keeps ₹300 from the deposit; ₹4,700 comes back to you.');
    expect(completedLine({ ...r, deposit: 0 })).toBe('Completed.');
  });

  it('turns the browser’s date-and-time boxes into exact times and back', () => {
    const d = new Date(2026, 10, 21, 9, 30);
    expect(toLocalInput(d)).toBe('2026-11-21T09:30');
    expect(fromLocalInput('2026-11-21T09:30')).toBe(d.toISOString());
    expect(fromLocalInput('')).toBeNull();
    expect(fromLocalInput('not a date')).toBeNull();
  });
});
