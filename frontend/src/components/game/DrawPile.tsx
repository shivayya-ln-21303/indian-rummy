import { useState } from 'react';
import { useGameStore } from '../../store/gameStore';

export default function DrawPile() {
  const { gameState, playerId, drawFromDeck } = useGameStore();
  const [animating, setAnimating] = useState(false);

  if (!gameState) return null;

  const isMyTurn = gameState.currentPlayerId === playerId;
  const hasDrawn = gameState.myCards.length === 14;
  const canDraw = isMyTurn && !hasDrawn && gameState.deckSize > 0;

  const handleDraw = () => {
    if (!canDraw) return;
    setAnimating(true);
    window.setTimeout(() => setAnimating(false), 260);
    drawFromDeck();
  };

  return (
    <button
      type="button"
      className={`draw-pile ${canDraw ? 'can-draw' : ''} ${animating ? 'is-drawing' : ''}`}
      onClick={handleDraw}
      disabled={!canDraw}
    >
      <div className="draw-pile-stack">
        <div className="draw-shadow-card stack-3" />
        <div className="draw-shadow-card stack-2" />
        <div className="draw-shadow-card stack-1" />
        <div className="draw-top-card">
          <div className="card-back-pattern">
            <span>♦</span>
            <span>♠</span>
            <span>♥</span>
            <span>♣</span>
          </div>
          <span className="deck-count-badge">{gameState.deckSize}</span>
        </div>
      </div>
      <span className="pile-label">పేక తీయండి</span>
      <span className="pile-count">డెక్క్‌లో {gameState.deckSize}</span>
    </button>
  );
}
