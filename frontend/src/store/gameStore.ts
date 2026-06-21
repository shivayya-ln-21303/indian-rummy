import { create } from 'zustand';
import type { Card, GameState, PlayerSummary, RoomStatus, WsResponse } from '../types/game.types';
import { wsService, getWsUrl } from '../services/websocket.service';

// ============================================================
// Store shape
// ============================================================

interface GameStore {
  // Identity
  playerId: string | null;
  playerName: string;
  roomCode: string | null;

  // Game state
  gameState: GameState | null;

  // UI state
  selectedCards: string[];       // cardIds selected in hand
  pendingGroups: Card[][];       // player's current grouping
  connectionStatus: 'disconnected' | 'connecting' | 'connected';
  errorMessage: string | null;
  notification: string | null;

  // Actions
  setPlayerName: (name: string) => void;
  connect: () => Promise<void>;
  createRoom: () => void;
  joinRoom: (code: string) => void;
  reconnect: () => void;
  drawFromDeck: () => void;
  drawFromDiscard: () => void;
  discardCard: (cardId: string) => void;
  rearrangeCards: (groups: Card[][]) => void;
  declareWin: (groups: Card[][], discardCardId?: string) => void;
  toggleCardSelection: (cardId: string) => void;
  clearSelection: () => void;
  clearError: () => void;
  clearNotification: () => void;
}

// ============================================================
// Store implementation
// ============================================================

export const useGameStore = create<GameStore>((set, get) => ({
  // Initial state
  playerId: null,
  playerName: '',
  roomCode: null,
  gameState: null,
  selectedCards: [],
  pendingGroups: [],
  connectionStatus: 'disconnected',
  errorMessage: null,
  notification: null,

  // ---------------------------------------------------------------------------
  // Setup
  // ---------------------------------------------------------------------------

  setPlayerName: (name) => set({ playerName: name }),

  connect: async () => {
    set({ connectionStatus: 'connecting' });
    try {
      await wsService.connect(getWsUrl());
      set({ connectionStatus: 'connected' });
      registerHandlers(set, get);
    } catch {
      set({ connectionStatus: 'disconnected', errorMessage: 'Could not connect to server.' });
    }
  },

  // ---------------------------------------------------------------------------
  // Room actions
  // ---------------------------------------------------------------------------

  createRoom: () => {
    const { playerName } = get();
    if (!playerName.trim()) { set({ errorMessage: 'Enter your name first.' }); return; }
    wsService.send('CREATE_ROOM', { playerName: playerName.trim() });
  },

  joinRoom: (code) => {
    const { playerName } = get();
    if (!playerName.trim()) { set({ errorMessage: 'Enter your name first.' }); return; }
    wsService.send('JOIN_ROOM', { roomCode: code.toUpperCase(), playerName: playerName.trim() });
  },

  reconnect: () => {
    const { playerId, roomCode } = get();
    if (playerId && roomCode) {
      wsService.send('RECONNECT', { roomCode, playerId });
    }
  },

  // ---------------------------------------------------------------------------
  // Game actions
  // ---------------------------------------------------------------------------

  drawFromDeck: () => wsService.send('DRAW_CARD'),

  drawFromDiscard: () => wsService.send('DRAW_FROM_DISCARD'),

  discardCard: (cardId) => {
    wsService.send('DISCARD_CARD', { cardId });
    set({ selectedCards: [] });
  },

  rearrangeCards: (groups) => {
    const groupIds = groups.map((g) => g.map((c) => c.cardId));
    wsService.send('REARRANGE_CARDS', { groups: groupIds });
    set({ pendingGroups: groups });
  },

  declareWin: (groups, discardCardId) => {
    const groupIds = groups.map((g) => g.map((c) => c.cardId));
    wsService.send('DECLARE_WIN', { groups: groupIds, discardCardId });
  },

  // ---------------------------------------------------------------------------
  // UI helpers
  // ---------------------------------------------------------------------------

  toggleCardSelection: (cardId) => {
    set((s) => {
      const sel = s.selectedCards.includes(cardId)
        ? s.selectedCards.filter((id) => id !== cardId)
        : [...s.selectedCards, cardId];
      return { selectedCards: sel };
    });
  },

  clearSelection: () => set({ selectedCards: [] }),
  clearError:     () => set({ errorMessage: null }),
  clearNotification: () => set({ notification: null }),
}));

// ============================================================
// WebSocket event handlers
// ============================================================

function registerHandlers(
  set: (partial: Partial<GameStore>) => void,
  get: () => GameStore
) {
  wsService.on('ROOM_CREATED', (res: WsResponse) => {
    if (!res.success) { set({ errorMessage: res.error }); return; }
    const d = res.data as { roomCode: string; playerId: string };
    set({ roomCode: d.roomCode, playerId: d.playerId });
  });

  wsService.on('PLAYER_JOINED', (res: WsResponse) => {
    if (!res.success) { set({ errorMessage: res.error }); return; }
    const d = res.data as { playerId?: string; roomCode?: string; gameState?: GameState; playerName?: string; playerCount?: number };
    if (d.playerId) {
      set({
        playerId:  d.playerId,
        roomCode:  d.roomCode ?? get().roomCode,
        gameState: d.gameState ?? get().gameState,
      });
    } else {
    set({ notification: `${d.playerName} చేరారు (${d.playerCount}/4)` });
    }
  });

  wsService.on('RECONNECTED', (res: WsResponse) => {
    if (!res.success) { set({ errorMessage: res.error }); return; }
    const d = res.data as { playerId: string; gameState: GameState };
    set({ playerId: d.playerId, gameState: d.gameState });
  });

  wsService.on('PLAYER_RECONNECTED', (res: WsResponse) => {
    const d = res.data as { playerName: string };
    set({ notification: `${d.playerName} మళ్ళీ చేరారు` });
  });

  wsService.on('PLAYER_DISCONNECTED', (res: WsResponse) => {
    const d = res.data as { players?: PlayerSummary[] };
    if (d.players && get().gameState) {
      set({ gameState: { ...get().gameState!, players: d.players } });
    }
    set({ notification: 'ఒక ఆటగాడు విడిపోయాడు' });
  });

  wsService.on('GAME_STARTED', (res: WsResponse) => {
    const d = res.data as { roomCode: string; players: PlayerSummary[] };
    const gs = get().gameState;
    if (gs) {
      set({
        gameState: { ...gs, status: 'DEALING', players: d.players ?? gs.players },
        notification: '🃏 ఆట మొదలవుతోంది — పేకలు పంచుతున్నాం!',
      });
    } else {
      set({ notification: '🃏 ఆట మొదలవుతోంది — పేకలు పంచుతున్నాం!' });
    }
  });

  wsService.on('CARD_DISTRIBUTED', (res: WsResponse) => {
    if (!res.success) return;
    const d = res.data as { cards: Card[]; deckSize: number; topDiscard: Card | null };
    const gs = get().gameState;
    if (gs) {
      set({ gameState: { ...gs, status: 'PLAYING', myCards: d.cards, deckSize: d.deckSize, topDiscard: d.topDiscard } });
    }
  });

  wsService.on('TURN_CHANGED', (res: WsResponse) => {
    if (!res.success) return;
    const d = res.data as { players: PlayerSummary[]; currentPlayerId: string; timeLeft: number };
    const gs = get().gameState;
    if (!gs) return;
    set({
      gameState: {
        ...gs,
        players:         d.players,
        currentPlayerId: d.currentPlayerId,
        turnTimeLeft:    d.timeLeft,
      },
    });
  });

  wsService.on('CARD_DRAWN', (res: WsResponse) => {
    if (!res.success) return;
    const d = res.data as { card?: Card; deckSize: number; mustDiscard?: boolean };
    const gs = get().gameState;
    if (!gs) return;
    const newCards = d.card ? [...gs.myCards, d.card] : gs.myCards;
    set({ gameState: { ...gs, myCards: newCards, deckSize: d.deckSize } });
  });

  wsService.on('CARD_DRAWN_FROM_DISCARD', (res: WsResponse) => {
    if (!res.success) return;
    const d = res.data as { card: Card; newTopDiscard: Card | null; playerId: string; deckSize: number };
    const gs = get().gameState;
    if (!gs) return;
    const isMe = d.playerId === get().playerId;
    set({
      gameState: {
        ...gs,
        myCards:    isMe ? [...gs.myCards, d.card] : gs.myCards,
        topDiscard: d.newTopDiscard,
        deckSize:   d.deckSize,
      },
    });
  });

  wsService.on('CARD_DISCARDED', (res: WsResponse) => {
    if (!res.success) return;
    const d = res.data as { card: Card; playerId: string; nextPlayerId: string };
    const gs = get().gameState;
    if (!gs) return;
    const isMe = d.playerId === get().playerId;
    const newCards = isMe
      ? gs.myCards.filter((c) => c.cardId !== d.card.cardId)
      : gs.myCards;
    set({ gameState: { ...gs, myCards: newCards, topDiscard: d.card } });
  });

  wsService.on('JOKER_UNLOCKED', (res: WsResponse) => {
    const gs = get().gameState;
    if (gs) {
      set({
        gameState:    { ...gs, jokerUnlocked: true, status: 'JOKER_UNLOCKED' },
        notification: '🃏 జోకర్ అన్‌లాక్! అడవి పేక వాడవచ్చు.',
      });
    }
  });

  wsService.on('PLAYER_WON', (res: WsResponse) => {
    if (!res.success) return;
    const d = res.data as { winnerId: string; winnerName: string };
    const gs = get().gameState;
    if (gs) {
      set({ gameState: { ...gs, status: 'FINISHED', winnerId: d.winnerId, winnerName: d.winnerName } });
    }
  });

  wsService.on('TURN_TIMEOUT', (res: WsResponse) => {
    set({ notification: 'సమయం అయిపోయింది — తదుపరి వంతు!' });
  });

  wsService.on('CARDS_REARRANGED', () => {
    // Acknowledgement; already updated locally
  });

  wsService.on('ERROR', (res: WsResponse) => {
    set({ errorMessage: res.error ?? 'An error occurred.' });
  });
}

