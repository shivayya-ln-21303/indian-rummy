import { useGameStore } from '../../store/gameStore';

/**
 * Renders the 3 opponent players around the table.
 * On mobile: top opponent (seat opposite), and two side opponents.
 */
export default function OtherPlayers() {
  const { gameState, playerId } = useGameStore();
  if (!gameState) return null;

  const others = gameState.players.filter((p) => p.playerId !== playerId);

  return (
    <div className="opponents-row">
      {others.map((player) => (
        <div key={player.playerId} className="opponent" style={{ position: 'relative' }}>
          {player.isCurrentTurn && <div className="active-turn-ring" />}
          <div className="opponent-name" title={player.playerName}>
            {player.playerName}
          </div>
          {/* Show face-down cards */}
          <div className="opponent-cards">
            {Array.from({ length: Math.min(player.cardCount, 7) }).map((_, i) => (
              <div key={i} className="opponent-card-back" />
            ))}
          </div>
          <div className="opponent-card-count">
            {player.cardCount} cards{!player.connected && ' (offline)'}
          </div>
        </div>
      ))}
    </div>
  );
}

