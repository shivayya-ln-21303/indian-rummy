// ============================================================
// Game domain types — mirroring the Java model
// ============================================================

export type Suit = 'HEARTS' | 'DIAMONDS' | 'CLUBS' | 'SPADES' | 'JOKER';
export type Rank =
  | 'TWO' | 'THREE' | 'FOUR' | 'FIVE' | 'SIX' | 'SEVEN' | 'EIGHT'
  | 'NINE' | 'TEN' | 'JACK' | 'QUEEN' | 'KING' | 'ACE' | 'JOKER';

export interface Card {
  cardId: string;
  suit: Suit;
  rank: Rank;
  joker: boolean;
  displayName?: string;
}

export type RoomStatus =
  | 'WAITING_FOR_PLAYERS'
  | 'DEALING'
  | 'PLAYING'
  | 'JOKER_UNLOCKED'
  | 'FINISHED';

export interface PlayerSummary {
  playerId: string;
  playerName: string;
  seatIndex: number;
  cardCount: number;
  connected: boolean;
  isCurrentTurn: boolean;
}

export interface GameState {
  roomId: string;
  roomCode: string;
  status: RoomStatus;
  players: PlayerSummary[];
  myCards: Card[];
  myGroups: Card[][];
  topDiscard: Card | null;
  deckSize: number;
  currentPlayerId: string | null;
  currentPlayerName: string | null;
  jokerUnlocked: boolean;
  turnTimeLeft: number;
  winnerId: string | null;
  winnerName: string | null;
}

// ============================================================
// WebSocket protocol types
// ============================================================

export interface WsMessage {
  type: string;
  payload?: unknown;
}

export interface WsResponse {
  type: string;
  success: boolean;
  data?: unknown;
  error?: string;
  timestamp?: string;
}

// ============================================================
// UI helpers
// ============================================================

export const SUIT_SYMBOL: Record<Suit, string> = {
  HEARTS:   '♥',
  DIAMONDS: '♦',
  CLUBS:    '♣',
  SPADES:   '♠',
  JOKER:    '🃏',
};

export const RANK_DISPLAY: Record<Rank, string> = {
  TWO:   '2',  THREE: '3',  FOUR:  '4',  FIVE:  '5',
  SIX:   '6',  SEVEN: '7',  EIGHT: '8',  NINE:  '9',
  TEN:   '10', JACK:  'J',  QUEEN: 'Q',  KING:  'K',
  ACE:   'A',  JOKER: '🃏',
};

export const isRedSuit = (suit: Suit): boolean =>
  suit === 'HEARTS' || suit === 'DIAMONDS';

