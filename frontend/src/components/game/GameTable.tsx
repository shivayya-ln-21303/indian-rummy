import OtherPlayers from './OtherPlayers';
import DrawPile     from './DrawPile';
import DiscardPile  from './DiscardPile';
import TurnTimer    from './TurnTimer';
import JokerStatus  from './JokerStatus';
import PlayerHand   from './PlayerHand';
import ErrorBanner  from '../common/ErrorBanner';
import { useGameStore } from '../../store/gameStore';

export default function GameTable() {
  const { gameState, playerId, errorMessage, clearError } = useGameStore();
  if (!gameState) return null;

  const isMyTurn = gameState.currentPlayerId === playerId;
  const hasDrawn  = gameState.myCards.length === 14;

  return (
    <div className="game-table">
      {errorMessage && <ErrorBanner message={errorMessage} onClose={clearError} />}

      {/* Timer (top-left) */}
      <TurnTimer />

      {/* Joker status (top-right) */}
      <JokerStatus />

      {/* Opponents (top row) */}
      <OtherPlayers />

      {/* Centre: draw + discard piles */}
      <div className="table-center">
        <DrawPile />

        {/* Turn indicator badge */}
        <div style={{ textAlign: 'center' }}>
          {isMyTurn ? (
            <div
              style={{
                background: 'rgba(255,215,0,0.15)',
                border: '1px solid #ffd700',
                borderRadius: 20,
                padding: '4px 12px',
                fontSize: '0.7rem',
                color: '#ffd700',
                fontWeight: 700,
              }}
            >
              {hasDrawn ? '→ Discard or Declare Win' : '← Draw a card'}
            </div>
          ) : (
            <div
              style={{
                background: 'rgba(0,0,0,0.3)',
                borderRadius: 20,
                padding: '4px 12px',
                fontSize: '0.65rem',
                color: 'rgba(255,255,255,0.5)',
              }}
            >
              {gameState.players.find(p => p.isCurrentTurn)?.playerName ?? '…'}'s turn
            </div>
          )}
        </div>

        <DiscardPile />
      </div>

      {/* Player's hand (bottom) */}
      <PlayerHand />
    </div>
  );
}

