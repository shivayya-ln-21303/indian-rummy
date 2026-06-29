import { useGameStore } from '../../store/gameStore';

export default function JokerStatus() {
  const { gameState, playerId } = useGameStore();
  if (!gameState) return null;

  const myStatus = gameState.players.find((player) => player.playerId === playerId)?.jokerUnlocked ?? gameState.jokerUnlocked;

  return (
    <div className={`joker-status-chip premium-panel-secondary ${myStatus ? 'active' : ''}`}>
      <span className="joker-chip-card">🃏</span>
      <div className="joker-chip-copy">
        <span className="joker-chip-title">జోకర్ పేక</span>
        <span className="joker-chip-state">{myStatus ? 'జోకర్ అన్‌లాక్' : 'జోకర్ లాక్'}</span>
      </div>
      <span className={`mini-badge ${myStatus ? 'gold' : 'dark'}`}>{myStatus ? 'నా జోకర్ సిద్ధం' : 'నా జోకర్ లాక్'}</span>
    </div>
  );
}
