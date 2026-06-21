import { useGameStore } from '../../store/gameStore';

export default function WaitingRoom() {
  const { gameState, playerId, roomCode } = useGameStore();
  const players = gameState?.players ?? [];

  const seats = Array.from({ length: 4 }, (_, i) => players[i] ?? null);

  const copyCode = () => {
    if (roomCode) navigator.clipboard.writeText(roomCode).catch(() => {});
  };

  return (
    <div className="waiting">
      <div style={{ fontSize: '48px' }}>🃏</div>
      <h2>Waiting for Players</h2>

      <div style={{ textAlign: 'center' }}>
        <p style={{ fontSize: '0.75rem', opacity: 0.7, marginBottom: 6 }}>Room Code</p>
        <div className="room-code-display" onClick={copyCode} style={{ cursor: 'pointer' }}>
          {gameState?.roomCode ?? roomCode}
        </div>
        <p style={{ fontSize: '0.65rem', opacity: 0.5, marginTop: 6 }}>Tap to copy</p>
      </div>

      <div className="waiting-seats">
        {seats.map((player, i) => (
          <div key={i} className={`seat ${player ? 'filled' : ''}`}>
            {player ? (
              <>
                <div style={{ fontSize: '20px' }}>
                  {player.playerId === playerId ? '👤' : '🙂'}
                </div>
                <div className="seat-name">{player.playerName}</div>
                {player.playerId === playerId && (
                  <div style={{ fontSize: '0.6rem', color: '#ffd700' }}>You</div>
                )}
              </>
            ) : (
              <>
                <div style={{ fontSize: '20px', opacity: 0.3 }}>👤</div>
                <div className="seat-empty pulse">Waiting…</div>
              </>
            )}
          </div>
        ))}
      </div>

      <p style={{ fontSize: '0.8rem', opacity: 0.7 }}>
        {players.length}/4 players joined
      </p>
      <p style={{ fontSize: '0.75rem', opacity: 0.5, textAlign: 'center', maxWidth: 280 }}>
        Game starts automatically when all 4 players join
      </p>
    </div>
  );
}

