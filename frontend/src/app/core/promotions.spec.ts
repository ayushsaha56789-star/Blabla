import { CATEGORY_LABELS, clickRate, promoStatus, reach } from './promotions-api';

describe('promotions on screen', () => {
  const later = new Date(Date.now() + 5 * 86_400_000).toISOString().slice(0, 10);
  const earlier = new Date(Date.now() - 5 * 86_400_000).toISOString().slice(0, 10);

  it('says where a promotion stands in plain words', () => {
    expect(promoStatus({ status: 'DRAFT', live: false, endsOn: later }).text).toBe('Draft');
    expect(promoStatus({ status: 'IN_REVIEW', live: false, endsOn: later }).text).toBe('Being checked');
    expect(promoStatus({ status: 'REJECTED', live: false, endsOn: later }).tone).toBe('danger');
    expect(promoStatus({ status: 'APPROVED', live: true, endsOn: later })).toEqual({ text: 'Live', tone: 'success' });
    expect(promoStatus({ status: 'APPROVED', live: false, endsOn: later }).text).toBe('Approved: starts soon');
    expect(promoStatus({ status: 'APPROVED', live: false, endsOn: earlier }).text).toBe('Ended');
    expect(promoStatus({ status: 'TAKEN_DOWN', live: false, endsOn: later }).text).toBe('Taken down');
  });

  it('counts views and clicks in plain words', () => {
    expect(reach(1, 1)).toBe('1 view · 1 click');
    expect(reach(0, 0)).toBe('0 views · 0 clicks');
    expect(reach(12, 2)).toBe('12 views · 2 clicks');
  });

  it('shows the share of people who tapped', () => {
    expect(clickRate(0, 0)).toBe('–');
    expect(clickRate(200, 3)).toBe('1.5%');
    expect(clickRate(3, 1)).toBe('33.3%');
  });

  it('has a label for every category', () => {
    for (const key of ['FOOD', 'SHOP', 'SERVICES', 'EDUCATION', 'EVENTS', 'TRAVEL', 'FITNESS', 'CREATOR', 'TECH', 'OTHER']) {
      expect(CATEGORY_LABELS[key as keyof typeof CATEGORY_LABELS]).toBeTruthy();
    }
  });
});
