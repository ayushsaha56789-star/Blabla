import { BADGE_ICON, levelProgress } from './social-api';

describe('rewards on screen', () => {
  it('shows progress through the current level', () => {
    expect(levelProgress({ xp: 175, levelStartXp: 100, nextLevelXp: 250 })).toBe(50);
    expect(levelProgress({ xp: 100, levelStartXp: 100, nextLevelXp: 250 })).toBe(0);
    expect(levelProgress({ xp: 20_000, levelStartXp: 12_000, nextLevelXp: null })).toBe(100);
  });

  it('has an icon for every badge', () => {
    for (const key of ['FIRST_RIDE', 'REGULAR', 'CAPTAIN', 'ECO_HERO', 'ROAD_TRIPPER', 'NIGHT_OWL', 'EARLY_BIRD', 'SOCIAL', 'ON_A_ROLL', 'AMBASSADOR']) {
      expect(BADGE_ICON[key]).toBeTruthy();
    }
  });
});
