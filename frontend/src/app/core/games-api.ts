import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { MemberCard } from './rides.models';

/** Chess and ludo. The server runs every game; the page draws it and sends moves. */
export type Game = 'CHESS' | 'LUDO';
export type GameMode = 'COMPUTER' | 'FRIENDS' | 'QUICK' | 'RIDE';
export type Level = 'EASY' | 'MEDIUM' | 'HARD';
export type RoomStatus = 'WAITING' | 'PLAYING' | 'FINISHED' | 'ABANDONED';

export interface SeatView {
  seat: number;
  member: MemberCard | null;
  name: string;
  computer: boolean;
  away: boolean;
  you: boolean;
  outcome: 'WIN' | 'LOSS' | 'DRAW' | null;
  ratingChange: number | null;
}

export interface ChessView {
  fen: string;
  whiteToMove: boolean;
  legal: string[];
  moves: string[];
  lastMove: string | null;
  check: boolean;
  whiteMs: number | null;
  blackMs: number | null;
}

export interface LudoView {
  seats: number[];
  tokens: number[][];
  current: number;
  dice: number | null;
  movable: number[];
  canRoll: boolean;
  last: string | null;
  lastMove: number[] | null;
}

export interface GameView {
  id: string;
  game: Game;
  mode: GameMode;
  code: string | null;
  status: RoomStatus;
  version: number;
  maxPlayers: number;
  level: Level | null;
  seats: SeatView[];
  mySeat: number;
  turnSeat: number | null;
  deadline: string | null;
  host: boolean;
  rated: boolean;
  result: string | null;
  chess: ChessView | null;
  ludo: LudoView | null;
  messages: { seat: number; name: string; phrase: string; at: string }[];
  serverNow: string;
}

export interface GameSummary {
  id: string;
  game: Game;
  mode: GameMode;
  status: RoomStatus;
  players: string[];
  myTurn: boolean;
  outcome: 'WIN' | 'LOSS' | 'DRAW' | null;
  result: string | null;
  updatedAt: string;
}

export interface LeaderRow {
  rank: number;
  member: MemberCard;
  rating: number;
  played: number;
  won: number;
}

@Injectable({ providedIn: 'root' })
export class GamesApi {
  private readonly http = inject(HttpClient);

  create(body: { game: Game; mode: 'COMPUTER' | 'FRIENDS'; level?: Level; players?: number; colour?: 'WHITE' | 'BLACK' | 'RANDOM' }):
    Promise<GameView> {
    return firstValueFrom(this.http.post<GameView>('/api/games', body));
  }

  join(code: string): Promise<GameView> {
    return firstValueFrom(this.http.post<GameView>('/api/games/join', { code }));
  }

  quick(game: Game): Promise<GameView> {
    return firstValueFrom(this.http.post<GameView>('/api/games/quick', { game }));
  }

  ride(rideId: string, game: Game): Promise<GameView> {
    return firstValueFrom(this.http.post<GameView>(`/api/games/ride/${encodeURIComponent(rideId)}`, { game }));
  }

  mine(): Promise<GameSummary[]> {
    return firstValueFrom(this.http.get<GameSummary[]>('/api/games/mine'));
  }

  leaderboard(game: Game): Promise<{ game: Game; top: LeaderRow[]; me: LeaderRow | null }> {
    return firstValueFrom(this.http.get<{ game: Game; top: LeaderRow[]; me: LeaderRow | null }>('/api/games/leaderboard',
      { params: new HttpParams().set('game', game) }));
  }

  /** The room, or null when nothing changed since `since`. */
  async room(id: string, since: number | null): Promise<GameView | null> {
    let params = new HttpParams();
    if (since !== null) params = params.set('since', since);
    const res = await firstValueFrom(this.http.get<GameView>(`/api/games/${encodeURIComponent(id)}`, { params, observe: 'response' }));
    return res.status === 204 ? null : res.body;
  }

  start(id: string, fillWithComputer: boolean): Promise<GameView> {
    return firstValueFrom(this.http.post<GameView>(`/api/games/${encodeURIComponent(id)}/start`, { fillWithComputer }));
  }

  leave(id: string): Promise<void> {
    return firstValueFrom(this.http.post<void>(`/api/games/${encodeURIComponent(id)}/leave`, null));
  }

  move(id: string, move: string): Promise<GameView> {
    return firstValueFrom(this.http.post<GameView>(`/api/games/${encodeURIComponent(id)}/move`, { move }));
  }

  resign(id: string): Promise<GameView> {
    return firstValueFrom(this.http.post<GameView>(`/api/games/${encodeURIComponent(id)}/resign`, null));
  }

  roll(id: string): Promise<GameView> {
    return firstValueFrom(this.http.post<GameView>(`/api/games/${encodeURIComponent(id)}/roll`, null));
  }

  token(id: string, token: number): Promise<GameView> {
    return firstValueFrom(this.http.post<GameView>(`/api/games/${encodeURIComponent(id)}/token`, { token }));
  }

  say(id: string, phrase: string): Promise<GameView> {
    return firstValueFrom(this.http.post<GameView>(`/api/games/${encodeURIComponent(id)}/say`, { phrase }));
  }
}

/** "Ravi and 2 computer players" rather than every computer by name. */
export function gamePlayers(names: string[]): string {
  const people = names.filter((n) => !n.startsWith('Computer ('));
  const computers = names.length - people.length;
  const parts = [...people];
  if (computers) parts.push(computers === 1 && !people.length ? 'the computer' : `${computers} computer ${computers === 1 ? 'player' : 'players'}`);
  return parts.length ? parts.join(' and ') : 'nobody yet';
}

// ----- Chat -----

/** What the set phrases say. Emojis are sent as they are. */
export const PHRASES: Record<string, string> = {
  HELLO: 'Hello! 👋', GG: 'Good game!', WP: 'Well played', NICE: 'Nice move!', OOPS: 'Oops!', THINK: 'Thinking…',
  THANKS: 'Thanks!', HURRY: 'Your turn 🙂',
};
export const EMOJIS = ['👍', '😂', '😮', '😅', '🎉', '🔥', '🙏', '😎'];

export function phraseText(p: string): string {
  return PHRASES[p] ?? p;
}

// ----- Chess -----

/** The board from a FEN: 64 squares, a1 first; each is '' or a piece letter (upper case white, lower case black). */
export function fenBoard(fen: string): string[] {
  const squares = Array<string>(64).fill('');
  const rows = fen.split(' ')[0].split('/');
  rows.forEach((row, i) => {
    const rank = 7 - i;
    let file = 0;
    for (const c of row) {
      if (c >= '1' && c <= '8') file += Number(c);
      else squares[rank * 8 + file++] = c;
    }
  });
  return squares;
}

/** "e4" → 28. */
export function squareIndex(name: string): number {
  return (name.charCodeAt(1) - 49) * 8 + (name.charCodeAt(0) - 97);
}

export function squareName(i: number): string {
  return String.fromCharCode(97 + (i % 8)) + String.fromCharCode(49 + Math.floor(i / 8));
}

/** Solid glyphs for both sides (coloured by CSS), shown as text rather than emoji. */
export const PIECE_GLYPH: Record<string, string> = {
  k: '♚︎', q: '♛︎', r: '♜︎', b: '♝︎', n: '♞︎', p: '♟︎',
};

/** "m:ss" for a chess clock. */
export function clockText(ms: number): string {
  const s = Math.max(0, Math.ceil(ms / 1000));
  return `${Math.floor(s / 60)}:${String(s % 60).padStart(2, '0')}`;
}

// ----- Ludo -----

export const LUDO_COLOURS = ['Orange', 'Green', 'Blue', 'Purple'];

/** The 52 shared squares on a 15 × 15 board as [row, col], from Orange's start square, clockwise. */
export const TRACK: [number, number][] = [
  [6, 1], [6, 2], [6, 3], [6, 4], [6, 5],
  [5, 6], [4, 6], [3, 6], [2, 6], [1, 6], [0, 6],
  [0, 7], [0, 8],
  [1, 8], [2, 8], [3, 8], [4, 8], [5, 8],
  [6, 9], [6, 10], [6, 11], [6, 12], [6, 13], [6, 14],
  [7, 14], [8, 14],
  [8, 13], [8, 12], [8, 11], [8, 10], [8, 9],
  [9, 8], [10, 8], [11, 8], [12, 8], [13, 8], [14, 8],
  [14, 7], [14, 6],
  [13, 6], [12, 6], [11, 6], [10, 6], [9, 6],
  [8, 5], [8, 4], [8, 3], [8, 2], [8, 1], [8, 0],
  [7, 0], [6, 0],
];

/** Each colour's home column (progress 51–55). */
export const HOME_COLUMN: [number, number][][] = [
  [[7, 1], [7, 2], [7, 3], [7, 4], [7, 5]],
  [[1, 7], [2, 7], [3, 7], [4, 7], [5, 7]],
  [[7, 13], [7, 12], [7, 11], [7, 10], [7, 9]],
  [[13, 7], [12, 7], [11, 7], [10, 7], [9, 7]],
];

/** Where finished tokens sit, in the centre. */
export const HOME_SPOT: [number, number][] = [[7, 6], [6, 7], [7, 8], [8, 7]];

/** The yard corners: top-left, top-right, bottom-right, bottom-left. */
export const YARD_ORIGIN: [number, number][] = [[0, 0], [0, 9], [9, 9], [9, 0]];

/** The safe squares (start squares and stars), as track indexes. */
export const SAFE_SQUARES = [0, 8, 13, 21, 26, 34, 39, 47];

/** Where a token stands on the 15 × 15 board, as [row, col]. */
export function ludoCell(colour: number, token: number, progress: number): [number, number] {
  if (progress < 0) {
    const [r, c] = YARD_ORIGIN[colour];
    const slot = [[1, 1], [1, 4], [4, 1], [4, 4]][token];
    return [r + slot[0], c + slot[1]];
  }
  if (progress <= 50) return TRACK[(colour * 13 + progress) % 52];
  if (progress <= 55) return HOME_COLUMN[colour][progress - 51];
  return HOME_SPOT[colour];
}
