import { useGameStore } from '../../store/gameStore';

export default function OtherPlayers() {
  const { gameState, playerId } = useGameStore();
  if (!gameState) return null;

  const others = gameState.players.filter((p) => p.playerId !== playerId);

  return (
    <div className="opponents-row">
      {others.map((player) => (
        <div
          key={player.playerId}
          className={`opponent${player.isCurrentTurn ? ' active-player' : ''}`}
        >
          {player.isCurrentTurn && <div className="active-turn-ring" />}
          <div className="opponent-name" title={player.playerName}>
            {player.playerName}
          </div>
          <div className="opponent-cards">
            {Array.from({ length: Math.min(player.cardCount, 6) }).map((_, i) => (
              <div key={i} className="opponent-card-back" />
            ))}
          </div>
          <div className="opponent-card-count">
            {player.cardCount} పేకలు{!player.connected && ' ⚠️'}
          </div>
          {player.isCurrentTurn && (
            <div className="opponent-turn-badge">వంతు ▶</div>
          )}
        </div>
      ))}
    </div>
  );
}

