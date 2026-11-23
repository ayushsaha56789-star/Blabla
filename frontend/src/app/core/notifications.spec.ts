import { dayLabel, noticeIcon } from './notifications-api';

describe('notifications on screen', () => {
  it('groups notices by day in plain words', () => {
    const now = new Date(2026, 10, 23, 18, 0);
    expect(dayLabel(new Date(2026, 10, 23, 9, 15).toISOString(), now)).toBe('Today');
    expect(dayLabel(new Date(2026, 10, 22, 23, 59).toISOString(), now)).toBe('Yesterday');
    expect(dayLabel(new Date(2026, 10, 16, 8, 0).toISOString(), now)).toContain('16');
  });

  it('only uses icons the app has', () => {
    expect(noticeIcon('star')).toBe('star');
    expect(noticeIcon('<script>')).toBe('bell');
  });
});
