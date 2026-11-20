import { bookingStatus } from './rides-format';
import { feeFor, paise, walletLineLabel, WalletLine } from './payments-api';

describe('money on screen', () => {
  it('shows paise as rupees, with paise only when there are some', () => {
    expect(paise(60000)).toBe('₹600');
    expect(paise(905)).toBe('₹9.05');
    expect(paise(12345600)).toBe('₹1,23,456');
    expect(paise(-30000)).toBe('−₹300');
  });

  it('works out the service fee like the server (rounded to the paisa)', () => {
    expect(feeFor(60000, 0)).toBe(0);
    expect(feeFor(60000, 5)).toBe(3000);
    expect(feeFor(33333, 2.5)).toBe(833);
  });

  it('explains wallet lines in plain words', () => {
    const line = (kind: string, account: 'CREDITS' | 'EARNINGS'): WalletLine =>
      ({ at: '', kind, account, amountPaise: 0, memo: null, paymentId: null });
    expect(walletLineLabel(line('SETTLE', 'EARNINGS'))).toBe('Earned from a trip');
    expect(walletLineLabel(line('REFUND', 'CREDITS'))).toBe('Refund as credits');
    expect(walletLineLabel(line('REFUND', 'EARNINGS'))).toBe('Late cancellation: your share');
    expect(walletLineLabel(line('GOODWILL', 'CREDITS'))).toBe('Gift from SaathChalo');
  });

  it('tells passengers a seat is waiting for their payment', () => {
    expect(bookingStatus('AWAITING_PAYMENT')).toEqual({ text: 'Waiting for payment', tone: 'warning' });
  });
});
