import { clockText, fenBoard, gamePlayers, HOME_SPOT, ludoCell, phraseText, squareIndex, squareName, TRACK } from './games-api';

describe('games on screen', () => {
  it('reads a chess position and names squares', () => {
    const board = fenBoard('rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq e3 0 1');
    expect(board[squareIndex('e1')]).toBe('K');
    expect(board[squareIndex('e8')]).toBe('k');
    expect(board[squareIndex('e4')]).toBe('P');
    expect(board[squareIndex('e2')]).toBe('');
    expect(squareName(0)).toBe('a1');
    expect(squareName(63)).toBe('h8');
    expect(clockText(600_000)).toBe('10:00');
    expect(clockText(61_200)).toBe('1:02');
    expect(clockText(-5)).toBe('0:00');
  });

  it('places ludo tokens on the board', () => {
    // 52 different shared squares, each touching the one before (diagonally only at the four inner corners).
    expect(new Set(TRACK.map((c) => c.join(','))).size).toBe(52);
    let diagonals = 0;
    TRACK.forEach((cell, i) => {
      const next = TRACK[(i + 1) % 52];
      const dr = Math.abs(cell[0] - next[0]);
      const dc = Math.abs(cell[1] - next[1]);
      expect(Math.max(dr, dc)).toBe(1);
      if (dr + dc === 2) diagonals++;
    });
    expect(diagonals).toBe(4);
    // Each colour starts next to its own yard, and turns into its home column from its last track square.
    expect(ludoCell(0, 0, 0)).toEqual([6, 1]);
    expect(ludoCell(1, 0, 0)).toEqual([1, 8]);
    expect(ludoCell(2, 0, 0)).toEqual([8, 13]);
    expect(ludoCell(3, 0, 0)).toEqual([13, 6]);
    expect(ludoCell(0, 0, 50)).toEqual([7, 0]);
    expect(ludoCell(0, 0, 51)).toEqual([7, 1]);
    expect(ludoCell(2, 3, 56)).toEqual(HOME_SPOT[2]);
    expect(ludoCell(1, 2, -1)).toEqual([4, 10]);
    expect(gamePlayers(['Computer (easy)', 'Computer (easy)', 'Computer (easy)'])).toBe('3 computer players');
    expect(gamePlayers(['Computer (hard)'])).toBe('the computer');
    expect(gamePlayers(['Ravi', 'Computer (medium)'])).toBe('Ravi and 1 computer player');
    expect(gamePlayers([])).toBe('nobody yet');
    expect(phraseText('GG')).toBe('Good game!');
    expect(phraseText('🎉')).toBe('🎉');
  });
});
