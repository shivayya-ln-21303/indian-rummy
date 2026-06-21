import { useEffect, useState } from 'react';
import { useGameStore } from '../../store/gameStore';

const TOTAL_SECONDS = 30;

export default function TurnTimer() {
  const { gameState, playerId } = useGameStore();
  const [timeLeft, setTimeLeft] = useState(TOTAL_SECONDS);

  const isMyTurn = gameState?.currentPlayerId === playerId;
  const serverTime = gameState?.turnTimeLeft ?? TOTAL_SECONDS;

  // Sync with server-provided time, then count down locally
  useEffect(() => {
    setTimeLeft(serverTime > 0 ? serverTime : TOTAL_SECONDS);
  }, [gameState?.currentPlayerId]);

  useEffect(() => {
    if (!gameState || gameState.status === 'FINISHED') return;
    const interval = setInterval(() => {
      setTimeLeft((t) => Math.max(0, t - 1));
    }, 1000);
    return () => clearInterval(interval);
  }, [gameState?.currentPlayerId]);

  const fraction    = timeLeft / TOTAL_SECONDS;
  const radius      = 18;
  const circumference = 2 * Math.PI * radius;
  const offset      = circumference * (1 - fraction);
  const urgent      = timeLeft <= 10;

  if (!gameState) return null;

  const currentPlayer = gameState.players.find((p) => p.playerId === gameState.currentPlayerId);

  return (
    <div className="turn-timer">
      <div className="timer-ring">
        <svg width="44" height="44" viewBox="0 0 44 44">
          <circle className="timer-track" cx="22" cy="22" r={radius} />
          <circle
            className={`timer-fill${urgent ? ' urgent' : ''}`}
            cx="22" cy="22" r={radius}
            strokeDasharray={circumference}
            strokeDashoffset={offset}
          />
        </svg>
        <div className="timer-text" style={{ color: urgent ? '#e63946' : '#fff' }}>
          {timeLeft}
        </div>
      </div>
      <div style={{ fontSize: '0.55rem', color: isMyTurn ? '#ffd700' : 'rgba(255,255,255,0.6)', marginTop: 2, textAlign: 'center', maxWidth: 60, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
        {isMyTurn ? 'YOUR TURN' : (currentPlayer?.playerName ?? '…')}
      </div>
    </div>
  );
}

