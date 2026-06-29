import { useMemo, useState } from 'react';
import { useGameStore } from '../../store/gameStore';
import CardComponent from './CardComponent';
import type { Card } from '../../types/game.types';

function StackPreview({ card }: { card: Card | null }) {
  if (!card) {
    return <div className="discard-preview-empty">-</div>;
  }
  return <CardComponent card={card} small />;
}

export default function PlayerDiscardPiles() {
  const { gameState, playerId } = useGameStore();
  const [expandedPlayerId, setExpandedPlayerId] = useState<string | null>(null);

  const expandedPlayer = useMemo(
    () => gameState?.players.find((player) => player.playerId === expandedPlayerId) ?? null,
    [expandedPlayerId, gameState?.players],
  );

  if (!gameState) return null;

  return (
    <>
      <div className="player-discard-piles">
        {gameState.players.map((player) => {
          const discards = gameState.playerDiscards[player.playerId] ?? [];
          const topCard = discards[0] ?? null;
          const isMe = player.playerId === playerId;
          const canOpen = discards.length > 0;

          return (
            <button
              type="button"
              key={player.playerId}
              className={`discard-stack-card premium-panel-secondary ${player.isCurrentTurn ? 'active' : ''}`}
              onClick={() => canOpen && setExpandedPlayerId(player.playerId)}
              disabled={!canOpen}
            >
              <div className="discard-stack-name-row">
                <span className="discard-stack-name">{isMe ? 'మీరు' : player.playerName}</span>
                {player.jokerUnlocked && <span className="joker-mini">🃏</span>}
              </div>
              <div className="discard-stack-preview">
                <StackPreview card={topCard} />
              </div>
              <div className="discard-stack-count">({discards.length})</div>
            </button>
          );
        })}
      </div>

      {expandedPlayer && (
        <div className="discard-modal-backdrop" onClick={() => setExpandedPlayerId(null)}>
          <div className="discard-modal premium-panel" onClick={(e) => e.stopPropagation()}>
            <div className="discard-modal-header">
              <div>
                <p className="eyebrow">పడేసిన పేకల చరిత్ర</p>
                <h3>{expandedPlayer.playerName}</h3>
              </div>
              <button type="button" className="icon-btn" onClick={() => setExpandedPlayerId(null)}>✕</button>
            </div>

            <div className="discard-modal-grid">
              {(gameState.playerDiscards[expandedPlayer.playerId] ?? []).map((card, index) => (
                <div key={`${card.cardId}-${index}`} className="discard-modal-card">
                  <CardComponent card={card} small />
                  <span className="discard-order">#{index + 1}</span>
                </div>
              ))}
            </div>
          </div>
        </div>
      )}
    </>
  );
}
