import { useEffect, useMemo, useState } from 'react';
import { useGameStore } from '../../store/gameStore';
import { getStats, recordLoss, recordWin, useStats } from '../../store/useStats';

export default function WinnerDialog() {
  const { gameState, playerId } = useGameStore();
  const stats = useStats();
  const [showStats, setShowStats] = useState(false);

  const isWinner = gameState?.winnerId === playerId;

  useEffect(() => {
    if (!gameState?.winnerId || !playerId) return;
    const key = `rummy_stats_recorded_${gameState.roomCode}_${gameState.winnerId}_${playerId}`;
    try {
      if (window.sessionStorage.getItem(key)) return;
      if (isWinner) {
        recordWin();
      } else {
        recordLoss();
      }
      window.sessionStorage.setItem(key, '1');
    } catch {
      const snapshot = getStats();
      if (snapshot.gamesPlayed === 0 || snapshot.gamesWon + snapshot.gamesLost === 0) {
        if (isWinner) recordWin();
        else recordLoss();
      }
    }
  }, [gameState?.roomCode, gameState?.winnerId, isWinner, playerId]);

  const rankings = useMemo(() => {
    if (!gameState) return [];
    const players = [...gameState.players];
    return players.sort((a, b) => {
      if (a.playerId === gameState.winnerId) return -1;
      if (b.playerId === gameState.winnerId) return 1;
      if (a.cardCount !== b.cardCount) return a.cardCount - b.cardCount;
      return a.seatIndex - b.seatIndex;
    });
  }, [gameState]);

  if (!gameState?.winnerId) return null;

  return (
    <div className="overlay winner-overlay">
      <div className="winner-dialog premium-panel">
        <div className="confetti-rain" aria-hidden="true">
          {['🎉', '✨', '🃏', '🎊', '🏆', '💫'].map((item, index) => (
            <span key={`${item}-${index}`} style={{ animationDelay: `${index * 0.15}s` }}>{item}</span>
          ))}
        </div>

        <div className="winner-hero">
          <div className="winner-trophy">{isWinner ? '🏆' : '😢'}</div>
          <h2>{isWinner ? 'మీరు గెలిచారు!' : `${gameState.winnerName ?? 'ఎవరో'} గెలిచారు!`}</h2>
          <p>{isWinner ? 'అద్భుతం! మీ సమూహాలు పూర్తయ్యాయి.' : 'ఈ మ్యాచ్ ముగిసింది. తదుపరి రౌండ్‌లో గెలవండి!'}</p>
        </div>

        <div className="winner-summary premium-panel-secondary">
          <div className="stats-inline">
            <span>{stats.gamesPlayed} గేమ్‌లు జాయించారు</span>
            <span>{stats.gamesWon} గెలిచారు</span>
            <span>ప్రస్తుత స్ట్రీక్ {stats.currentStreak}</span>
          </div>
        </div>

        <div className="ranking-table-wrap premium-panel-secondary">
          <h3>Player Rankings</h3>
          <div className="ranking-table">
            <div className="ranking-head">
              <span>ర్యాంక్</span>
              <span>ఆటగాడు</span>
              <span>మిగిలిన పేకలు</span>
              <span>జోకర్</span>
            </div>
            {rankings.map((player, index) => (
              <div key={player.playerId} className={`ranking-row ${player.playerId === gameState.winnerId ? 'winner' : ''}`}>
                <span>#{index + 1}</span>
                <span>{player.playerName}</span>
                <span>{player.cardCount}</span>
                <span>{player.jokerUnlocked ? 'అన్‌లాక్' : 'లాక్'}</span>
              </div>
            ))}
          </div>
        </div>

        {showStats && (
          <div className="stats-detail premium-panel-secondary">
            <h3>📊 స్టాట్స్</h3>
            <div className="stats-grid">
              <div><span>ఆడినవి</span><strong>{stats.gamesPlayed}</strong></div>
              <div><span>గెలిచినవి</span><strong>{stats.gamesWon}</strong></div>
              <div><span>ఓడినవి</span><strong>{stats.gamesLost}</strong></div>
              <div><span>ప్రస్తుత స్ట్రీక్</span><strong>{stats.currentStreak}</strong></div>
              <div><span>ఉత్తమ స్ట్రీక్</span><strong>{stats.bestStreak}</strong></div>
            </div>
          </div>
        )}

        <div className="winner-actions">
          <button type="button" className="btn btn-gold" onClick={() => window.location.reload()}>🔄 మళ్ళీ ఆడండి</button>
          <button type="button" className="btn btn-secondary" onClick={() => window.location.reload()}>🏠 లాబీకి వెళ్ళు</button>
          <button type="button" className="btn btn-blue" onClick={() => setShowStats((prev) => !prev)}>📊 స్టాట్స్</button>
        </div>
      </div>
    </div>
  );
}
