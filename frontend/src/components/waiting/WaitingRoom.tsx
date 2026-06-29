import { useMemo } from 'react';
import { useGameStore } from '../../store/gameStore';

const SEAT_AVATARS = ['🎴', '🃏', '♠', '♥'];

export default function WaitingRoom() {
  const { gameState, playerId, roomCode, isCreator, startGame } = useGameStore();
  const players = gameState?.players ?? [];
  const code = gameState?.roomCode ?? roomCode ?? '';
  const canStart = isCreator && players.length >= 2;
  const inviteLink = useMemo(() => `${window.location.origin}?room=${code}`, [code]);

  const seats = Array.from({ length: 4 }, (_, index) =>
    players.find((player) => player.seatIndex === index) ?? null,
  );

  const copyCode = async () => {
    if (!code) return;
    try {
      await navigator.clipboard.writeText(code);
    } catch {
      // ignore clipboard failures
    }
  };

  const shareInvite = async () => {
    if (!code) return;
    const text = `రమ్మీ పేకాట గది కోడ్: ${code}`;
    try {
      if (navigator.share) {
        await navigator.share({ title: 'రమ్మీ పేకాట', text, url: inviteLink });
        return;
      }
      await navigator.clipboard.writeText(`${text} ${inviteLink}`.trim());
    } catch {
      // ignore share failures
    }
  };

  return (
    <div className="waiting-screen">
      <div className="waiting-shell premium-panel fade-in">
        <div className="waiting-header">
          <div>
            <p className="eyebrow">స్నేహితుల కోసం సిద్ధంగా ఉంది</p>
            <h2>ఆటగాళ్ల కోసం వేచి ఉంది</h2>
            <p className="waiting-subtitle">గది కోడ్‌ను పంచుకుని అందరూ చేరిన తరువాత ఆట మొదలుపెట్టండి</p>
          </div>
          <div className="waiting-room-code glow-panel">
            <span className="mini-label">గది కోడ్</span>
            <div className="premium-room-code">{code}</div>
            <div className="room-code-actions compact">
              <button className="btn btn-secondary" onClick={copyCode}>కాపీ</button>
              <button className="btn btn-secondary" onClick={shareInvite}>షేర్</button>
            </div>
          </div>
        </div>

        <div className="player-count-chip">{players.length}/4 మంది చేరారు</div>

        <div className="waiting-seats-grid">
          {seats.map((player, index) => {
            const isMe = player?.playerId === playerId;
            const isCreatorSeat = player?.playerId === gameState?.creatorId;
            return (
              <div key={index} className={`seat-card premium-panel-secondary ${player ? 'filled' : 'empty'}`}>
                <div className="seat-avatar">{SEAT_AVATARS[index]}</div>
                <div className="seat-main">
                  <div className="seat-title-row">
                    <span className="seat-name">{player?.playerName ?? 'వేచి ఉంది...'}</span>
                    {player && <span className="online-dot" />}
                  </div>
                  <div className="seat-meta-row">
                    {isMe && <span className="mini-badge gold">మీరు</span>}
                    {isCreatorSeat && <span className="mini-badge dark">👑 క్రియేటర్</span>}
                    {!player && <span className="waiting-pulse">ఇంకా ఖాళీ</span>}
                  </div>
                </div>
              </div>
            );
          })}
        </div>

        <div className="waiting-footer">
          {isCreator ? (
            <div className="creator-start-block">
              <button className="btn btn-gold btn-xl pulse-gold" onClick={startGame} disabled={!canStart}>
                🎮 ఆట మొదలు పెట్టు
              </button>
              <p className="helper-text">{canStart ? 'అందరూ సిద్ధమైతే వెంటనే మొదలుపెట్టండి' : 'కనీసం 2 మంది ఉండాలి'}</p>
            </div>
          ) : (
            <div className="waiting-note premium-panel-secondary">
              క్రియేటర్ ఆట మొదలు పెట్టాలి
              <span className="loading-dots"><span>.</span><span>.</span><span>.</span></span>
            </div>
          )}

          <button className="btn btn-secondary" onClick={shareInvite}>ఆహ్వాన లింక్ పంచుకోండి</button>
        </div>
      </div>
    </div>
  );
}
