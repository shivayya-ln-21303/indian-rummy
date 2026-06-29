import OtherPlayers from './OtherPlayers';
import DrawPile from './DrawPile';
import DiscardPile from './DiscardPile';
import JokerStatus from './JokerStatus';
import PlayerHand from './PlayerHand';
import PlayerDiscardPiles from './PlayerDiscardPiles';
import ErrorBanner from '../common/ErrorBanner';
import { useGameStore } from '../../store/gameStore';

export default function GameTable() {
  const { gameState, playerId, errorMessage, clearError } = useGameStore();
  if (!gameState) return null;

  const isMyTurn = gameState.currentPlayerId === playerId;
  const hasDrawn = gameState.myCards.length === 14;
  const currentPlayerName = gameState.players.find((player) => player.isCurrentTurn)?.playerName ?? '...';

  return (
    <div className="game-table">
      {errorMessage && <ErrorBanner message={errorMessage} onClose={clearError} />}

      <section className="game-opponents">
        <div className="top-status-row">
          <JokerStatus />
        </div>
        <OtherPlayers />
      </section>

      <section className="game-turn-banner">
        <div className={`turn-banner ${isMyTurn ? 'mine' : 'other'}`}>
          {isMyTurn
            ? (hasDrawn ? 'పేక వేయండి లేదా గెలుపు ప్రకటించండి' : 'మీ వంతు — పేక తీసుకోండి')
            : `${currentPlayerName} వంతు కొనసాగుతోంది`}
        </div>
      </section>

      <section className="game-center">
        <div className="table-center premium-panel-secondary">
          <DrawPile />
          <div className="table-center-divider" />
          <DiscardPile />
        </div>
      </section>

      <section className="game-discards">
        <div className="discards-header">
          <span>పడేసిన పేకలు</span>
          <span className="mini-label">ఆటగాడి పేరుపై నొక్కండి</span>
        </div>
        <PlayerDiscardPiles />
      </section>

      <section className="game-player-hand">
        <PlayerHand />
      </section>
    </div>
  );
}
