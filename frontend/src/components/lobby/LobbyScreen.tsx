import { useState } from 'react';
import { useGameStore } from '../../store/gameStore';
import ErrorBanner from '../common/ErrorBanner';

export default function LobbyScreen() {
  const { playerName, roomCode, setPlayerName, createRoom, joinRoom, errorMessage, clearError } = useGameStore();
  const [joinCode, setJoinCode] = useState('');

  return (
    <div className="lobby">
      {errorMessage && <ErrorBanner message={errorMessage} onClose={clearError} />}

      <div className="lobby-logo">🃏</div>
      <h1>రమ్మీ పేకాట</h1>
      <p style={{ opacity: 0.75, fontSize: '0.95rem', fontWeight: 600 }}>4 మంది ఆడే పేకాట ఆట</p>

      {/* Name input */}
      <div className="lobby-card">
        <h2>మీ పేరు</h2>
        <input
          className="input"
          type="text"
          placeholder="మీ పేరు టైప్ చేయండి…"
          maxLength={20}
          value={playerName}
          onChange={(e) => setPlayerName(e.target.value)}
          onKeyDown={(e) => e.key === 'Enter' && createRoom()}
        />
      </div>

      {/* Create room */}
      <div className="lobby-card">
        <h2>🎮 గది తయారు చేయండి</h2>
        <p style={{ opacity: 0.72, fontSize: '0.88rem' }}>
          కొత్త ఆట మొదలుపెట్టండి. కోడ్ స్నేహితులకు పంచండి.
        </p>
        <button className="btn btn-primary" onClick={createRoom} disabled={!playerName.trim()}>
          + గది తయారు చేయండి
        </button>
        {roomCode && (
          <div style={{ textAlign: 'center', marginTop: 8 }}>
            <p style={{ fontSize: '0.8rem', opacity: 0.75 }}>ఈ కోడ్ పంచుకోండి:</p>
            <div className="room-code-display" style={{ fontSize: '1.9rem', padding: '10px 22px', marginTop: 6 }}>
              {roomCode}
            </div>
            <p style={{ fontSize: '0.75rem', opacity: 0.6, marginTop: 8 }}>
              ఇంకా 3 మంది కోసం వేచి ఉంది...
            </p>
          </div>
        )}
      </div>

      <div className="divider">లేదా</div>

      {/* Join room */}
      <div className="lobby-card">
        <h2>🚪 గదిలో చేరండి</h2>
        <input
          className="input"
          type="text"
          placeholder="గది కోడ్ వేయండి (ఉదా: AB12CD)"
          maxLength={6}
          value={joinCode}
          onChange={(e) => setJoinCode(e.target.value.toUpperCase())}
          onKeyDown={(e) => e.key === 'Enter' && joinCode.length === 6 && joinRoom(joinCode)}
          style={{ letterSpacing: '5px', textTransform: 'uppercase', fontFamily: 'monospace', fontSize: '1.2rem' }}
        />
        <button
          className="btn btn-green"
          onClick={() => joinRoom(joinCode)}
          disabled={!playerName.trim() || joinCode.length !== 6}
        >
          గదిలో చేరండి →
        </button>
      </div>
    </div>
  );
}

