import { useMemo, useState } from 'react';
import { useGameStore } from '../../store/gameStore';
import ErrorBanner from '../common/ErrorBanner';

const SUITS = ['♠', '♥', '♦', '♣'];

export default function LobbyScreen() {
  const { playerName, roomCode, setPlayerName, createRoom, joinRoom, errorMessage, clearError } = useGameStore();
  const [joinCode, setJoinCode] = useState('');
  const shareUrl = useMemo(() => {
    if (!roomCode) return '';
    return `${window.location.origin}?room=${roomCode}`;
  }, [roomCode]);

  const handleCopy = async () => {
    if (!roomCode) return;
    try {
      await navigator.clipboard.writeText(roomCode);
    } catch {
      // ignore clipboard failures
    }
  };

  const handleShare = async () => {
    if (!roomCode) return;
    const text = `రమ్మీ పేకాటలో చేరండి — కోడ్: ${roomCode}`;
    try {
      if (navigator.share) {
        await navigator.share({ title: 'రమ్మీ పేకాట', text, url: shareUrl });
        return;
      }
      await navigator.clipboard.writeText(`${text} ${shareUrl}`.trim());
    } catch {
      // ignore share failures
    }
  };

  return (
    <div className="lobby-screen">
      {errorMessage && <ErrorBanner message={errorMessage} onClose={clearError} />}

      <div className="lobby-shell premium-panel slide-up">
        <div className="lobby-hero">
          <div className="suit-orbit" aria-hidden="true">
            {SUITS.map((suit, index) => (
              <span key={suit} className={`orbit-suit orbit-${index + 1}`}>{suit}</span>
            ))}
            <div className="hero-chip">🃏</div>
          </div>
          <div className="hero-copy">
            <p className="eyebrow">ప్రీమియం మల్టీప్లేయర్ అనుభవం</p>
            <h1>రమ్మీ పేకాట</h1>
            <p className="hero-subtitle">స్నేహితులతో చీకటి-బంగారు స్టైల్లో ఆడే రమ్మీ టేబుల్</p>
          </div>
        </div>

        <div className="lobby-content">
          <div className="input-card premium-panel-secondary">
            <label className="field-label">మీ పేరు</label>
            <div className="input-wrap">
              <span className="input-icon">👤</span>
              <input
                className="input premium-input"
                type="text"
                placeholder="మీ పేరు టైప్ చేయండి…"
                maxLength={20}
                value={playerName}
                onChange={(e) => setPlayerName(e.target.value)}
                onKeyDown={(e) => e.key === 'Enter' && createRoom()}
              />
            </div>
          </div>

          <div className="lobby-dual premium-panel-secondary">
            <section className="lobby-section">
              <div className="section-heading">
                <span className="section-icon">✨</span>
                <div>
                  <h2>కొత్త గది తయారు చేయండి</h2>
                  <p>స్నేహితులకు కోడ్ పంపించి వెంటనే ఆట మొదలుపెట్టండి</p>
                </div>
              </div>

              <button className="btn btn-gold btn-large" onClick={createRoom} disabled={!playerName.trim()}>
                కొత్త గది తయారు చేయండి
              </button>

              {roomCode && (
                <div className="room-code-panel glow-panel">
                  <span className="mini-label">మీ గది కోడ్</span>
                  <div className="premium-room-code">{roomCode}</div>
                  <div className="room-code-actions">
                    <button className="btn btn-secondary" onClick={handleCopy}>కాపీ చేయండి</button>
                    <button className="btn btn-secondary" onClick={handleShare}>పంచుకోండి</button>
                  </div>
                </div>
              )}
            </section>

            <div className="lobby-divider">
              <span>లేదా</span>
            </div>

            <section className="lobby-section">
              <div className="section-heading">
                <span className="section-icon">🚪</span>
                <div>
                  <h2>గదిలో చేరండి</h2>
                  <p>6 అక్షరాల కోడ్‌తో నేరుగా గేమ్ టేబుల్‌లోకి చేరండి</p>
                </div>
              </div>

              <div className="input-wrap code-input-wrap">
                <span className="input-icon">#</span>
                <input
                  className="input premium-input code-input"
                  type="text"
                  placeholder="AB12CD"
                  maxLength={6}
                  value={joinCode}
                  autoCapitalize="characters"
                  onChange={(e) => setJoinCode(e.target.value.toUpperCase())}
                  onKeyDown={(e) => e.key === 'Enter' && joinCode.length === 6 && joinRoom(joinCode)}
                />
              </div>

              <button
                className="btn btn-blue btn-large"
                onClick={() => joinRoom(joinCode)}
                disabled={!playerName.trim() || joinCode.length !== 6}
              >
                గదిలో చేరండి
              </button>
            </section>
          </div>
        </div>
      </div>
    </div>
  );
}
