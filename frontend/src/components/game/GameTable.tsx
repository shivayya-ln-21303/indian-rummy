import OtherPlayers from './OtherPlayers';
import DrawPile     from './DrawPile';
import DiscardPile  from './DiscardPile';
import JokerStatus  from './JokerStatus';
import PlayerHand   from './PlayerHand';
import PlayerDiscardPiles from './PlayerDiscardPiles';
import ErrorBanner  from '../common/ErrorBanner';
import { useGameStore } from '../../store/gameStore';

export default function GameTable() {
  const { gameState, playerId, errorMessage, clearError } = useGameStore();
  if (!gameState) return null;

  const isMyTurn = gameState.currentPlayerId === playerId;
  const hasDrawn  = gameState.myCards.length === 14;
  const currentPlayerName = gameState.players.find(p => p.isCurrentTurn)?.playerName ?? '…';
  const myJokerUnlocked = gameState.players.find(p => p.playerId === playerId)?.jokerUnlocked ?? false;

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
            {myJokerUnlocked && <span style={{ marginLeft: 8, color: '#ffd700', fontSize: '0.85rem' }}>🃏 జోకర్ అన్‌లాక్!</span>}
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

      {/* Per-player discard history (visible to everyone) */}
      <PlayerDiscardPiles />

      {/* Player's hand (bottom) */}
      <PlayerHand />
    </div>
  );
}

