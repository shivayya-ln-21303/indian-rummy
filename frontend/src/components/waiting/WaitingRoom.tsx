import { useGameStore } from '../../store/gameStore';

export default function WaitingRoom() {
  const { gameState, playerId, roomCode } = useGameStore();
  const players = gameState?.players ?? [];

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
      <p style={{ fontSize: '0.82rem', opacity: 0.55, textAlign: 'center', maxWidth: 300, lineHeight: 1.5 }}>
        4 మంది వస్తే ఆట మొదలవుతుంది
      </p>
    </div>
  );
}

