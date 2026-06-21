import { useGameStore } from '../../store/gameStore';

export default function WaitingRoom() {
  const { gameState, playerId, roomCode, isCreator, startGame } = useGameStore();
  const players = gameState?.players ?? [];
  const canStart = isCreator && players.length >= 2;

  const seats = Array.from({ length: 4 }, (_, i) => players[i] ?? null);

  const copyCode = () => {
    const code = gameState?.roomCode ?? roomCode ?? '';
    if (code) {
      navigator.clipboard.writeText(code).catch(() => {});
    }
  };

  return (
    <div className="waiting">
      <div style={{ fontSize: '54px' }}>🃏</div>
      <h2>ఆటగాళ్ళ కోసం వేచి ఉంది</h2>

      <div style={{ textAlign: 'center' }}>
        <p style={{ fontSize: '0.82rem', opacity: 0.75, marginBottom: 8, fontWeight: 600 }}>గది కోడ్</p>
        <div className="room-code-display" onClick={copyCode} style={{ cursor: 'pointer' }}>
          {gameState?.roomCode ?? roomCode}
        </div>
        <p style={{ fontSize: '0.72rem', opacity: 0.55, marginTop: 8 }}>👆 నొక్కితే కాపీ అవుతుంది</p>
      </div>

      <div className="waiting-seats">
        {seats.map((player, i) => (
          <div key={i} className={`seat ${player ? 'filled' : ''}`}>
            {player ? (
              <>
                <div style={{ fontSize: '24px' }}>
                  {player.playerId === playerId ? '🙋' : '🙂'}
                </div>
                <div className="seat-name">{player.playerName}</div>
                {player.playerId === playerId && (
                  <div style={{ fontSize: '0.65rem', color: '#ffd700', fontWeight: 700 }}>మీరు</div>
                )}
                {player.playerId === gameState?.creatorId && (
                  <div style={{ fontSize: '0.6rem', color: '#aef', fontWeight: 700 }}>👑 క్రియేటర్</div>
                )}
              </>
            ) : (
              <>
                <div style={{ fontSize: '24px', opacity: 0.3 }}>👤</div>
                <div className="seat-empty pulse">వేచి ఉంది...</div>
              </>
            )}
          </div>
        ))}
      </div>

      <p style={{ fontSize: '0.9rem', opacity: 0.75, fontWeight: 600 }}>
        {players.length}/4 మంది చేరారు
      </p>

      {isCreator ? (
        <div style={{ textAlign: 'center', marginTop: 16 }}>
          <button
            className={`btn btn-primary${canStart ? ' start-game-btn' : ''}`}
            onClick={startGame}
            disabled={!canStart}
            style={{
              fontSize: '1.1rem', padding: '12px 32px', borderRadius: 12,
              fontFamily: 'inherit', fontWeight: 800,
              background: canStart ? 'linear-gradient(135deg, #f39c12, #e74c3c)' : '#555',
              color: '#fff', border: 'none', cursor: canStart ? 'pointer' : 'not-allowed',
              boxShadow: canStart ? '0 4px 16px rgba(231,76,60,0.5)' : 'none',
              opacity: canStart ? 1 : 0.6,
            }}
          >
            🎮 ఆట మొదలు పెట్టు
          </button>
          {!canStart && (
            <p style={{ fontSize: '0.78rem', opacity: 0.6, marginTop: 8 }}>
              కనీసం 2 మంది ఉండాలి
            </p>
          )}
        </div>
      ) : (
        <p style={{ fontSize: '0.82rem', opacity: 0.55, textAlign: 'center', maxWidth: 300, lineHeight: 1.5 }}>
          రూమ్ క్రియేటర్ ఆట మొదలు పెట్టాలి
        </p>
      )}
    </div>
  );
}

