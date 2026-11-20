import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { MemberCard } from './rides.models';

/** Rewards (XP, badges, impact, invites), squads, event rides and vibe match. */
export interface Badge {
  key: string;
  name: string;
  how: string;
  earned: boolean;
  progress: number;
  goal: number;
}

export interface RewardsView {
  xp: number;
  level: number;
  levelName: string;
  levelStartXp: number;
  nextLevelXp: number | null;
  nextLevelName: string | null;
  impact: { trips: number; driven: number; km: number; people: number; rupees: number; co2Kg: number };
  streak: number;
  bestStreak: number;
  challenge: { title: string; done: number; goal: number; complete: boolean };
  badges: Badge[];
  referral: { code: string; invited: number; rewarded: number; creditRupees: number; canClaim: boolean; invitedByFriend: boolean;
    status: 'PENDING' | 'REWARDED' | null };
  board: { on: boolean; nickname: string | null };
}

export interface BoardRow {
  rank: number;
  nickname: string;
  co2Kg: number;
  trips: number;
  you: boolean;
}

export type SquadKind = 'COLLEGE' | 'OFFICE' | 'FRIENDS';

export interface SquadCard {
  id: string;
  name: string;
  kind: SquadKind;
  emailDomain: string | null;
  members: number;
  code: string;
  owner: boolean;
}

export interface RideCard {
  id: string;
  fromName: string;
  toName: string;
  departureAt: string;
  ladiesOnly: boolean;
  driver: MemberCard;
}

export interface EventCard {
  id: string;
  title: string;
  venue: string;
  placeId: number;
  placeName: string;
  state: string | null;
  startsAt: string;
  status: 'LIVE' | 'CANCELLED';
}

@Injectable({ providedIn: 'root' })
export class SocialApi {
  private readonly http = inject(HttpClient);

  rewards(): Promise<RewardsView> {
    return firstValueFrom(this.http.get<RewardsView>('/api/rewards/me'));
  }

  claim(code: string): Promise<RewardsView> {
    return firstValueFrom(this.http.post<RewardsView>('/api/rewards/claim', { code }));
  }

  board(on: boolean, nickname: string | null): Promise<RewardsView> {
    return firstValueFrom(this.http.put<RewardsView>('/api/rewards/board', { on, nickname }));
  }

  leaderboard(): Promise<{ month: string; top: BoardRow[]; me: BoardRow | null }> {
    return firstValueFrom(this.http.get<{ month: string; top: BoardRow[]; me: BoardRow | null }>('/api/rewards/leaderboard'));
  }

  squads(): Promise<SquadCard[]> {
    return firstValueFrom(this.http.get<SquadCard[]>('/api/community/squads'));
  }

  createSquad(name: string, kind: SquadKind, emailDomain: string | null): Promise<{ squad: SquadCard }> {
    return firstValueFrom(this.http.post<{ squad: SquadCard }>('/api/community/squads', { name, kind, emailDomain }));
  }

  joinSquad(code: string): Promise<{ squad: SquadCard }> {
    return firstValueFrom(this.http.post<{ squad: SquadCard }>('/api/community/squads/join', { code }));
  }

  squad(id: string): Promise<{ squad: SquadCard; members: MemberCard[]; rides: RideCard[] }> {
    return firstValueFrom(this.http.get<{ squad: SquadCard; members: MemberCard[]; rides: RideCard[] }>(
      `/api/community/squads/${encodeURIComponent(id)}`));
  }

  leaveSquad(id: string): Promise<void> {
    return firstValueFrom(this.http.post<void>(`/api/community/squads/${encodeURIComponent(id)}/leave`, null));
  }

  events(): Promise<EventCard[]> {
    return firstValueFrom(this.http.get<EventCard[]>('/api/community/events'));
  }

  event(id: string): Promise<{ event: EventCard; description: string | null; rides: RideCard[] }> {
    return firstValueFrom(this.http.get<{ event: EventCard; description: string | null; rides: RideCard[] }>(
      `/api/community/events/${encodeURIComponent(id)}`));
  }

  vibe(): Promise<{ interests: string[]; options: string[] }> {
    return firstValueFrom(this.http.get<{ interests: string[]; options: string[] }>('/api/community/vibe/me'));
  }

  saveInterests(interests: string[]): Promise<{ interests: string[]; options: string[] }> {
    return firstValueFrom(this.http.put<{ interests: string[]; options: string[] }>('/api/community/vibe/me', { interests }));
  }

  /** Vibe scores (5–99) with these members. */
  vibeScores(ids: string[]): Promise<Record<string, number>> {
    if (!ids.length) return Promise.resolve({});
    return firstValueFrom(this.http.get<Record<string, number>>('/api/community/vibe', { params: new HttpParams().set('ids', ids.join(',')) }));
  }
}

/** XP progress within the current level, 0–100. */
export function levelProgress(r: Pick<RewardsView, 'xp' | 'levelStartXp' | 'nextLevelXp'>): number {
  if (r.nextLevelXp === null) return 100;
  return Math.round(((r.xp - r.levelStartXp) / (r.nextLevelXp - r.levelStartXp)) * 100);
}

/** Badge emoji, so badges read at a glance. */
export const BADGE_ICON: Record<string, string> = {
  FIRST_RIDE: '🚗', REGULAR: '🔁', CAPTAIN: '🧑‍✈️', ECO_HERO: '🌱', ROAD_TRIPPER: '🛣️', NIGHT_OWL: '🦉', EARLY_BIRD: '🌅',
  SOCIAL: '🦋', ON_A_ROLL: '🔥', AMBASSADOR: '💌',
};

/** Draws a shareable "my impact" card (1080 × 1350, Instagram-friendly) and returns it as a PNG. */
export async function impactCard(r: RewardsView, name: string): Promise<Blob> {
  const canvas = document.createElement('canvas');
  canvas.width = 1080;
  canvas.height = 1350;
  const g = canvas.getContext('2d')!;
  const bg = g.createLinearGradient(0, 0, 1080, 1350);
  bg.addColorStop(0, '#047857');
  bg.addColorStop(1, '#0f172a');
  g.fillStyle = bg;
  g.fillRect(0, 0, 1080, 1350);
  g.fillStyle = '#ffffff';
  g.font = 'bold 54px system-ui, sans-serif';
  g.fillText('SaathChalo', 80, 140);
  g.font = '40px system-ui, sans-serif';
  g.fillStyle = 'rgba(255,255,255,0.8)';
  g.fillText(`${name} · Level ${r.level} · ${r.levelName}`, 80, 210);
  const tiles: [string, string][] = [
    [`${r.impact.co2Kg} kg`, 'CO₂ saved'],
    [`${r.impact.km.toLocaleString('en-IN')} km`, 'shared'],
    [`${r.impact.trips}`, r.impact.trips === 1 ? 'ride shared' : 'rides shared'],
    [`${r.impact.people}`, r.impact.people === 1 ? 'person met' : 'people met'],
  ];
  tiles.forEach(([big, small], i) => {
    const x = 80 + (i % 2) * 470;
    const y = 320 + Math.floor(i / 2) * 330;
    g.fillStyle = 'rgba(255,255,255,0.1)';
    g.beginPath();
    g.roundRect(x, y, 440, 290, 36);
    g.fill();
    g.fillStyle = '#ffffff';
    g.font = 'bold 96px system-ui, sans-serif';
    g.fillText(big, x + 40, y + 150);
    g.font = '40px system-ui, sans-serif';
    g.fillStyle = 'rgba(255,255,255,0.8)';
    g.fillText(small, x + 40, y + 220);
  });
  const earned = r.badges.filter((b) => b.earned).map((b) => BADGE_ICON[b.key] ?? '🏅').join('  ');
  g.font = '64px system-ui, sans-serif';
  g.fillText(earned || '🚗', 80, 1110);
  g.font = '36px system-ui, sans-serif';
  g.fillStyle = 'rgba(255,255,255,0.7)';
  g.fillText('Share the ride. Split the cost. Save the planet.', 80, 1230);
  return new Promise((resolve, reject) => canvas.toBlob((b) => (b ? resolve(b) : reject(new Error('No image'))), 'image/png'));
}
