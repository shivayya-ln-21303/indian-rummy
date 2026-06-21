import { useGameStore } from '../../store/gameStore';
import CardComponent from './CardComponent';

export default function DrawPile() {
  const { gameState, playerId, drawFromDeck } = useGameStore();
  if (!gameState) return null;

  const isMyTurn   = gameState.currentPlayerId === playerId;
  const hasDrawn   = gameState.myCards.length === 14;
  const canDraw    = isMyTurn && !hasDrawn && gameState.deckSize > 0;
  const deckSize   = gameState.deckSize;

  return (
    <div
      className="draw-pile"
      onClick={canDraw ? drawFromDeck : undefined}
      style={{ opacity: canDraw ? 1 : 0.6 }}
      title={canDraw ? 'Draw from deck' : ''}
    >
      <div className="draw-pile-stack">
        {deckSize > 2 && <div className="draw-pile-card" />}
        {deckSize > 1 && <div className="draw-pile-card" />}
        <div className="draw-pile-card" style={canDraw ? { cursor: 'pointer', transform: 'translate(0,0) scale(1.02)', transition: 'transform 0.15s' } : {}} />
      </div>
      <div className="pile-label">DECK</div>
      <div className="pile-count">{deckSize} left</div>
    </div>
  );
}

