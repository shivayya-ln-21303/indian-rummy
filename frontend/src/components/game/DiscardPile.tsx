import { useGameStore } from '../../store/gameStore';
import CardComponent from './CardComponent';

export default function DiscardPile() {
  const { gameState, playerId, drawFromDiscard } = useGameStore();
  if (!gameState) return null;

  const isMyTurn   = gameState.currentPlayerId === playerId;
  const hasDrawn   = gameState.myCards.length === 14;
  const canDraw    = isMyTurn && !hasDrawn && gameState.topDiscard !== null;
  const topDiscard = gameState.topDiscard;

  return (
    <div
      className="discard-pile"
      onClick={canDraw ? drawFromDiscard : undefined}
      title={canDraw ? 'Take discard' : ''}
      style={{ opacity: canDraw ? 1 : 0.7 }}
    >
      {topDiscard ? (
        <CardComponent
          card={topDiscard}
          onClick={canDraw ? drawFromDiscard : undefined}
        />
      ) : (
        <div className="discard-empty">Empty</div>
      )}
      <div className="pile-label">DISCARD</div>
    </div>
  );
}

