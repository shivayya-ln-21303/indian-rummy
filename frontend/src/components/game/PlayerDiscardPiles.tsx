import { useState } from 'react';
import { useGameStore } from '../../store/gameStore';
import CardComponent from './CardComponent';
import { RANK_DISPLAY, SUIT_SYMBOL } from '../../types/game.types';
import type { Card } from '../../types/game.types';

function CardMini({ card }: { card: Card }) {
  const red = card.suit === 'HEARTS' || card.suit === 'DIAMONDS';
  return (
    <div style={{
      display: 'inline-flex', flexDirection: 'column', alignItems: 'center',
      justifyContent: 'center',
      width: 38, height: 54, background: '#fffef7', borderRadius: 5,
      border: '1.5px solid #ccc', boxShadow: '0 1px 4px rgba(0,0,0,0.18)',
      fontSize: '0.7rem', fontWeight: 700,
      color: red ? '#c0392b' : '#1a1a2e',
      margin: '2px',
    }}>
      <span>{RANK_DISPLAY[card.rank]}</span>
      <span style={{ fontSize: '0.95rem' }}>{SUIT_SYMBOL[card.suit]}</span>
    </div>
  );
}

export default function PlayerDiscardPiles() {
  const { gameState, playerId } = useGameStore();
  const [expanded, setExpanded] = useState<string | null>(null);

  if (!gameState || !gameState.playerDiscards) return null;

  const players = gameState.players;
  if (players.length === 0) return null;

  return (
    <div className="player-discard-piles">
      {players.map(player => {
        const discards = gameState.playerDiscards[player.playerId] ?? [];
        const topCard = discards[0] ?? null;
        const isMe = player.playerId === playerId;
        const isOpen = expanded === player.playerId;

        return (
          <div key={player.playerId} className="player-discard-slot">
            <div
              className={`discard-slot-header${player.isCurrentTurn ? ' active-discard-slot' : ''}`}
              onClick={() => discards.length > 0 && setExpanded(isOpen ? null : player.playerId)}
              style={{ cursor: discards.length > 0 ? 'pointer' : 'default' }}
            >
              <span className="discard-slot-name">
                {isMe ? '🙋' : '🙂'} {player.playerName}
                {player.jokerUnlocked && <span style={{ marginLeft: 4, fontSize: '0.7rem', color: '#ffd700' }}>🃏</span>}
              </span>
              <div className="discard-slot-top">
                {topCard ? (
                  <CardMini card={topCard} />
                ) : (
                  <div className="discard-slot-empty">—</div>
                )}
                {discards.length > 1 && (
                  <span className="discard-count-badge">{discards.length}</span>
                )}
              </div>
            </div>

            {isOpen && (
              <div className="discard-pile-expanded">
                <div style={{ fontSize: '0.75rem', color: '#ffd', marginBottom: 6, fontWeight: 700 }}>
                  {player.playerName} పడేసిన పేకలు ({discards.length})
                </div>
                <div style={{ display: 'flex', flexWrap: 'wrap', gap: 4 }}>
                  {discards.map((c, i) => (
                    <CardMini key={i} card={c} />
                  ))}
                </div>
                <button
                  style={{ marginTop: 8, padding: '4px 12px', borderRadius: 6, border: 'none',
                    background: '#555', color: '#fff', cursor: 'pointer', fontSize: '0.78rem' }}
                  onClick={() => setExpanded(null)}
                >✕ మూయండి</button>
              </div>
            )}
          </div>
        );
      })}
    </div>
  );
}
