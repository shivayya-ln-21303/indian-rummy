import { useGameStore } from '../../store/gameStore';

export default function WinnerDialog() {
  const { gameState, playerId } = useGameStore();
  if (!gameState?.winnerId) return null;

  const isWinner = gameState.winnerId === playerId;
  const name     = gameState.winnerName ?? 'Someone';

  return (
    <div className="overlay">
      <div className="winner-dialog">
        <div className="winner-trophy">{isWinner ? '🏆' : '🎉'}</div>
        <div className="confetti">🎊 🃏 🎊</div>
        <h2>{isWinner ? 'You Won!' : `${name} Won!`}</h2>
        <p>
          {isWinner
            ? 'Congratulations! You formed the perfect sets.'
            : `${name} completed the winning declaration.`}
        </p>
        <button
          className="btn btn-primary"
          onClick={() => window.location.reload()}
        >
          Play Again
        </button>
      </div>
    </div>
  );
}

