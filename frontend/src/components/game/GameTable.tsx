import OtherPlayers from './OtherPlayers';
import DrawPile     from './DrawPile';
import DiscardPile  from './DiscardPile';
import JokerStatus  from './JokerStatus';
import PlayerHand   from './PlayerHand';
import ErrorBanner  from '../common/ErrorBanner';
import { useGameStore } from '../../store/gameStore';

export default function GameTable() {
  const { gameState, playerId, errorMessage, clearError } = useGameStore();
  if (!gameState) return null;

  const isMyTurn = gameState.currentPlayerId === playerId;
  const hasDrawn  = gameState.myCards.length === 14;
  const currentPlayerName = gameState.players.find(p => p.isCurrentTurn)?.playerName ?? '…';

  return (
    <div className="game-table">
      {errorMessage && <ErrorBanner message={errorMessage} onClose={clearError} />}

      {/* Joker status (top-right) */}
      <JokerStatus />

      {/* Opponents (top row) */}
      <OtherPlayers />

      {/* Turn indicator banner */}
      <div className="turn-banner">
        {isMyTurn ? (
          <div className="turn-badge-my">
            {hasDrawn ? '🃏 పేక వేయండి లేదా గెలుపు ప్రకటించండి' : '✋ పేక తీసుకోండి'}
          </div>
        ) : (
          <div className="turn-badge-other">
            ⏳ {currentPlayerName} వంతు
          </div>
        )}
      </div>

      {/* Centre: draw + discard piles */}
      <div className="table-center">
        <DrawPile />
        <DiscardPile />
      </div>

      {/* Player's hand (bottom) */}
      <PlayerHand />
    </div>
  );
}

