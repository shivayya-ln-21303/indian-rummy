import { useGameStore } from '../../store/gameStore';

export default function DrawPile() {
  const { gameState, playerId, drawFromDeck } = useGameStore();
  if (!gameState) return null;

  const isMyTurn   = gameState.currentPlayerId === playerId;
  const hasDrawn   = gameState.myCards.length === 14;
  const canDraw    = isMyTurn && !hasDrawn && gameState.deckSize > 0;
  const deckSize   = gameState.deckSize;

  return (
    <div
      className={`draw-pile${canDraw ? ' can-draw' : ''}`}
      onClick={canDraw ? drawFromDeck : undefined}
      style={{ opacity: canDraw ? 1 : 0.55 }}
    >
      <div className="draw-pile-stack">
        {deckSize > 2 && <div className="draw-pile-card" />}
        {deckSize > 1 && <div className="draw-pile-card" />}
        <div className="draw-pile-card" />
      </div>
      <div className="pile-label">🂠 గుంపు</div>
      <div className="pile-count">{deckSize} మిగిలినవి</div>
    </div>
  );
}

