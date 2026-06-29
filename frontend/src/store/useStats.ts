import { useEffect, useState } from 'react';

export interface Stats {
  gamesPlayed: number;
  gamesWon: number;
  gamesLost: number;
  currentStreak: number;
  bestStreak: number;
}

const STORAGE_KEY = 'rummy_stats_v1';
const UPDATE_EVENT = 'rummy-stats-updated';

const DEFAULT_STATS: Stats = {
  gamesPlayed: 0,
  gamesWon: 0,
  gamesLost: 0,
  currentStreak: 0,
  bestStreak: 0,
};

function safeWindow(): Window | null {
  return typeof window === 'undefined' ? null : window;
}

function sanitizeStats(value: unknown): Stats {
  if (!value || typeof value !== 'object') return DEFAULT_STATS;
  const candidate = value as Partial<Stats>;
  return {
    gamesPlayed: Number.isFinite(candidate.gamesPlayed) ? Math.max(0, Number(candidate.gamesPlayed)) : 0,
    gamesWon: Number.isFinite(candidate.gamesWon) ? Math.max(0, Number(candidate.gamesWon)) : 0,
    gamesLost: Number.isFinite(candidate.gamesLost) ? Math.max(0, Number(candidate.gamesLost)) : 0,
    currentStreak: Number.isFinite(candidate.currentStreak) ? Math.max(0, Number(candidate.currentStreak)) : 0,
    bestStreak: Number.isFinite(candidate.bestStreak) ? Math.max(0, Number(candidate.bestStreak)) : 0,
  };
}

function emitStatsUpdate() {
  const win = safeWindow();
  if (!win) return;
  try {
    win.dispatchEvent(new CustomEvent(UPDATE_EVENT));
  } catch {
    // ignore event errors
  }
}

function writeStats(stats: Stats) {
  const win = safeWindow();
  if (!win) return;
  try {
    win.localStorage.setItem(STORAGE_KEY, JSON.stringify(stats));
    emitStatsUpdate();
  } catch {
    // ignore storage errors
  }
}

export function getStats(): Stats {
  const win = safeWindow();
  if (!win) return DEFAULT_STATS;
  try {
    const raw = win.localStorage.getItem(STORAGE_KEY);
    if (!raw) return DEFAULT_STATS;
    return sanitizeStats(JSON.parse(raw));
  } catch {
    return DEFAULT_STATS;
  }
}

export function recordWin() {
  const current = getStats();
  const next: Stats = {
    gamesPlayed: current.gamesPlayed + 1,
    gamesWon: current.gamesWon + 1,
    gamesLost: current.gamesLost,
    currentStreak: current.currentStreak + 1,
    bestStreak: Math.max(current.bestStreak, current.currentStreak + 1),
  };
  writeStats(next);
}

export function recordLoss() {
  const current = getStats();
  const next: Stats = {
    gamesPlayed: current.gamesPlayed + 1,
    gamesWon: current.gamesWon,
    gamesLost: current.gamesLost + 1,
    currentStreak: 0,
    bestStreak: current.bestStreak,
  };
  writeStats(next);
}

export function useStats() {
  const [stats, setStats] = useState<Stats>(() => getStats());

  useEffect(() => {
    const win = safeWindow();
    if (!win) return undefined;

    const update = () => setStats(getStats());
    win.addEventListener('storage', update);
    win.addEventListener(UPDATE_EVENT, update);
    update();

    return () => {
      win.removeEventListener('storage', update);
      win.removeEventListener(UPDATE_EVENT, update);
    };
  }, []);

  return stats;
}
