import { useGameStore } from '../../store/gameStore';

const AVATARS = ['🎴', '🃏', '♠', '♥'];

export default function OtherPlayers() {
  const { gameState, playerId } = useGameStore();
  if (!gameState) return null;

  const others = gameState.players.filter((player) => player.playerId !== playerId);

  return (
    <div className="opponents-row">
      {others.map((player) => (
        <div key={player.playerId} className={`opponent-pod premium-panel-secondary ${player.isCurrentTurn ? 'active' : ''}`}>
          <div className="opponent-top-row">
            <div className="opponent-identity">
              <span className="opponent-avatar">{AVATARS[player.seatIndex % AVATARS.length]}</span>
              <div>
                <div className="opponent-name" title={player.playerName}>{player.playerName}</div>
                <div className="opponent-meta">
                  <span className={`status-dot ${player.connected ? 'online' : 'offline'}`} />
                  <span>{player.connected ? 'ఆన్‌లైన్' : 'ఆఫ్‌లైన్'}</span>
                  {player.jokerUnlocked && <span className="joker-mini">🃏</span>}
                </div>
              </div>
            </div>
          </div>

          <div className="opponent-hand-fan" aria-hidden="true">
            {Array.from({ length: Math.max(3, Math.min(player.cardCount, 5)) }).map((_, index) => (
              <div key={index} className="mini-card-back" style={{ transform: `rotate(${index * 6 - 12}deg) translateY(${Math.abs(index - 2) * 2}px)` }} />
            ))}
          </div>

          <div className="opponent-bottom-row">
            <span className="opponent-card-total">🂠 x{player.cardCount}</span>
            {player.isCurrentTurn && <span className="turn-pill">వంతు ▶</span>}
          </div>
        </div>
      ))}
    </div>
  );
}
