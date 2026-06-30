import { create } from 'zustand';
import type { Card, GameState, PlayerSummary, RoomStatus, WsResponse } from '../types/game.types';
import { wsService, getWsUrl } from '../services/websocket.service';

// ============================================================
// Session persistence helpers
// ============================================================

const SESSION_KEY = 'rummy_session_v2';
const NAME_KEY    = 'rummy_player_name';

interface SavedSession {
  playerId: string;
  roomCode: string;
  playerName: string;
  isCreator: boolean;
}

function saveSession(data: SavedSession) {
  try { localStorage.setItem(SESSION_KEY, JSON.stringify(data)); } catch { /* ignore */ }
}

function loadSession(): SavedSession | null {
  try {
    const raw = localStorage.getItem(SESSION_KEY);
    if (!raw) return null;
    const d = JSON.parse(raw) as Partial<SavedSession>;
    return (d?.playerId && d?.roomCode) ? (d as SavedSession) : null;
  } catch { return null; }
}

function clearSession() {
  try { localStorage.removeItem(SESSION_KEY); } catch { /* ignore */ }
}

function savePlayerName(name: string) {
  try { localStorage.setItem(NAME_KEY, name); } catch { /* ignore */ }
}

function loadPlayerName(): string {
  try { return localStorage.getItem(NAME_KEY) ?? ''; } catch { return ''; }
}

// ============================================================
// Store shape
// ============================================================

interface GameStore {
  // Identity
  playerId: string | null;
  playerName: string;
  roomCode: string | null;
  isCreator: boolean;
  isReconnecting: boolean;   // true while waiting for RECONNECTED after auto-reconnect

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
  startGame: () => void;
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
  // Initial state — restore playerName from localStorage immediately
  playerId: null,
  playerName: loadPlayerName(),
  roomCode: null,
  isCreator: false,
  isReconnecting: false,
  gameState: null,
  selectedCards: [],
  pendingGroups: [],
  connectionStatus: 'disconnected',
  errorMessage: null,
  notification: null,

  // ---------------------------------------------------------------------------
  // Setup
  // ---------------------------------------------------------------------------

  setPlayerName: (name) => {
    savePlayerName(name);
    set({ playerName: name });
  },

  connect: async () => {
    set({ connectionStatus: 'connecting' });
    try {
      await wsService.connect(getWsUrl());
      set({ connectionStatus: 'connected' });
      registerHandlers(set, get);

      // ── Auto-reconnect if a saved session exists ──
      const saved = loadSession();
      if (saved?.playerId && saved?.roomCode) {
        // Restore identity pre-emptively so screens render correctly while waiting
        set({
          playerId:       saved.playerId,
          roomCode:       saved.roomCode,
          isCreator:      saved.isCreator ?? false,
          isReconnecting: true,
          playerName:     saved.playerName || get().playerName,
        });
        wsService.send('RECONNECT', { roomCode: saved.roomCode, playerId: saved.playerId });
      }
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

  startGame: () => wsService.send('START_GAME'),

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
    const d = res.data as { roomCode: string; playerId: string; creatorId: string; gameState?: GameState };
    const { playerName } = get();
    saveSession({ playerId: d.playerId, roomCode: d.roomCode, playerName, isCreator: true });
    set({
      roomCode:  d.roomCode,
      playerId:  d.playerId,
      isCreator: true,
      gameState: d.gameState ?? {
        roomId: '', roomCode: d.roomCode, status: 'WAITING_FOR_PLAYERS',
        players: [], myCards: [], myGroups: [], topDiscard: null,
        deckSize: 0, currentPlayerId: null, currentPlayerName: null,
        jokerUnlocked: false, turnTimeLeft: 0, winnerId: null, winnerName: null,
        creatorId: d.creatorId, playerDiscards: {},
      },
    });
  });

  wsService.on('PLAYER_JOINED', (res: WsResponse) => {
    if (!res.success) { set({ errorMessage: res.error }); return; }
    const d = res.data as {
      playerId?: string; roomCode?: string; gameState?: GameState;
      playerName?: string; playerCount?: number; creatorId?: string;
      players?: PlayerSummary[];
    };
    if (d.playerId) {
      // This player just joined — persist session
      const { playerName } = get();
      const isCreator = d.playerId === d.creatorId;
      saveSession({ playerId: d.playerId, roomCode: d.roomCode ?? '', playerName, isCreator });
      set({
        playerId:  d.playerId,
        roomCode:  d.roomCode ?? get().roomCode,
        isCreator,
        gameState: d.gameState ?? get().gameState,
      });
    } else {
      // Another player joined — update player list in waiting room
      const gs = get().gameState;
      if (gs && d.players) {
        set({ gameState: { ...gs, players: d.players } });
      }
      set({ notification: `${d.playerName} చేరారు (${d.playerCount}/4)` });
    }
  });

  wsService.on('RECONNECTED', (res: WsResponse) => {
    if (!res.success) {
      // Reconnect failed — session is stale, clear it and go to lobby
      clearSession();
      set({
        isReconnecting: false,
        playerId: null, roomCode: null, gameState: null, isCreator: false,
        errorMessage: res.error ?? 'Session expired. Please rejoin.',
      });
      return;
    }
    const d = res.data as { playerId: string; creatorId?: string; gameState: GameState };
    const { playerName } = get();
    const isCreator = d.playerId === (d.creatorId ?? d.gameState?.creatorId);
    // Refresh saved session with latest state
    saveSession({ playerId: d.playerId, roomCode: d.gameState.roomCode, playerName, isCreator });
    set({
      isReconnecting: false,
      playerId:       d.playerId,
      isCreator,
      gameState:      d.gameState,
      // Restore pending groups from server-side saved groups so grouping survives refresh
      pendingGroups:  d.gameState.myGroups ?? [],
    });
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
    const d = res.data as {
      card: Card; playerId: string; nextPlayerId: string;
      playerDiscards?: Record<string, Card[]>;
    };
    const gs = get().gameState;
    if (!gs) return;
    const isMe = d.playerId === get().playerId;
    const newCards = isMe
      ? gs.myCards.filter((c) => c.cardId !== d.card.cardId)
      : gs.myCards;
    set({ gameState: {
      ...gs,
      myCards: newCards,
      topDiscard: d.card,
      playerDiscards: d.playerDiscards ?? gs.playerDiscards,
    }});
  });

  wsService.on('JOKER_UNLOCKED', (res: WsResponse) => {
    const d = res.data as { playerId?: string; playerName?: string };
    const gs = get().gameState;
    if (gs) {
      const myId = get().playerId;
      const isMe = d.playerId === myId;
      const updatedPlayers = gs.players.map(p =>
        p.playerId === d.playerId ? { ...p, jokerUnlocked: true } : p
      );
      set({
        gameState: {
          ...gs,
          players: updatedPlayers,
          jokerUnlocked: isMe ? true : gs.jokerUnlocked,
        },
        notification: isMe
          ? '🃏 జోకర్ అన్‌లాక్! అడవి పేక వాడవచ్చు.'
          : `🃏 ${d.playerName ?? 'ఒక ఆటగాడు'} జోకర్ అన్‌లాక్ చేసారు!`,
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
    // Game over — clear session so next visit starts fresh
    clearSession();
  });

  wsService.on('TURN_TIMEOUT', (_res: WsResponse) => {
    set({ notification: 'సమయం అయిపోయింది — తదుపరి వంతు!' });
  });

  wsService.on('CARDS_REARRANGED', () => {
    // Acknowledgement; already updated locally
  });

  wsService.on('ERROR', (res: WsResponse) => {
    const { isReconnecting } = get();
    if (isReconnecting) {
      // Auto-reconnect failed (room gone, player not found, etc.)
      clearSession();
      set({
        isReconnecting: false,
        playerId: null, roomCode: null, gameState: null, isCreator: false,
        notification: 'Session expired — please rejoin.',
      });
      return;
    }
    set({ errorMessage: res.error ?? 'An error occurred.' });
  });
}

