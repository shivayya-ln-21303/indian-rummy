import { useGameStore } from '../../store/gameStore';
import CardComponent from './CardComponent';

export default function DiscardPile() {
  const { gameState, playerId, drawFromDiscard } = useGameStore();
  if (!gameState) return null;

  const isMyTurn = gameState.currentPlayerId === playerId;
  const hasDrawn = gameState.myCards.length === 14;
  const canDraw = isMyTurn && !hasDrawn && gameState.topDiscard !== null;

  return (
    <button
      type="button"
      className={`discard-pile ${canDraw ? 'can-draw' : ''}`}
      onClick={canDraw ? drawFromDiscard : undefined}
      disabled={!canDraw}
    >
      <div className="discard-card-shell">
        {gameState.topDiscard ? (
          <CardComponent card={gameState.topDiscard} animClass={canDraw ? 'card-draw-ready' : undefined} />
        ) : (
          <div className="discard-empty">ఖాళీ</div>
        )}
      </div>
      <span className="pile-label">పడేసిన పేక</span>
    </button>
  );
}
