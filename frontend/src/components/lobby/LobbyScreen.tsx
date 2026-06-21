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
      <h1>Indian Rummy</h1>
      <p style={{ opacity: 0.7, fontSize: '0.85rem' }}>4-Player Multiplayer Card Game</p>

      {/* Name input */}
      <div className="lobby-card">
        <h2>Your Name</h2>
        <input
          className="input"
          type="text"
          placeholder="Enter your name…"
          maxLength={20}
          value={playerName}
          onChange={(e) => setPlayerName(e.target.value)}
          onKeyDown={(e) => e.key === 'Enter' && createRoom()}
        />
      </div>

      {/* Create room */}
      <div className="lobby-card">
        <h2>Create Room</h2>
        <p style={{ opacity: 0.7, fontSize: '0.8rem' }}>
          Start a new game. Share the code with friends.
        </p>
        <button className="btn btn-primary" onClick={createRoom} disabled={!playerName.trim()}>
          + Create Room
        </button>
        {roomCode && (
          <div style={{ textAlign: 'center', marginTop: 8 }}>
            <p style={{ fontSize: '0.75rem', opacity: 0.7 }}>Share this code:</p>
            <div className="room-code-display" style={{ fontSize: '1.8rem', padding: '10px 20px' }}>
              {roomCode}
            </div>
            <p style={{ fontSize: '0.7rem', opacity: 0.6, marginTop: 6 }}>
              Waiting for 3 more players…
            </p>
          </div>
        )}
      </div>

      <div className="divider">or</div>

      {/* Join room */}
      <div className="lobby-card">
        <h2>Join Room</h2>
        <input
          className="input"
          type="text"
          placeholder="Enter room code (e.g. AB12CD)"
          maxLength={6}
          value={joinCode}
          onChange={(e) => setJoinCode(e.target.value.toUpperCase())}
          onKeyDown={(e) => e.key === 'Enter' && joinCode.length === 6 && joinRoom(joinCode)}
          style={{ letterSpacing: '4px', textTransform: 'uppercase' }}
        />
        <button
          className="btn btn-secondary"
          onClick={() => joinRoom(joinCode)}
          disabled={!playerName.trim() || joinCode.length !== 6}
        >
          Join Room
        </button>
      </div>
    </div>
  );
}

