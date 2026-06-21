import { useGameStore } from '../../store/gameStore';

export default function WinnerDialog() {
  const { gameState, playerId } = useGameStore();
  if (!gameState?.winnerId) return null;

  const isWinner = gameState.winnerId === playerId;
  const name     = gameState.winnerName ?? 'ఎవరో';

  return (
    <div className="overlay">
      <div className="winner-dialog">
        <div className="winner-trophy">{isWinner ? '🏆' : '🎉'}</div>
        <div className="confetti">🎊 🃏 🎊</div>
        <h2>{isWinner ? 'మీరు గెలిచారు! 🎉' : `${name} గెలిచారు!`}</h2>
        <p>
          {isWinner
            ? 'అభినందనలు! మీరు పరిపూర్ణ సమూహాలు తయారు చేశారు.'
            : `${name} గెలుపు ప్రకటించారు. బాగా ఆడారు!`}
        </p>
        <button
          className="btn btn-primary"
          onClick={() => window.location.reload()}
        >
          🔄 మళ్ళీ ఆడండి
        </button>
      </div>
    </div>
  );
}

