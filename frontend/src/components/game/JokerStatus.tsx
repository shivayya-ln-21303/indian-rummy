import { useGameStore } from '../../store/gameStore';

export default function JokerStatus() {
  const { gameState } = useGameStore();
  if (!gameState) return null;

  const unlocked = gameState.jokerUnlocked;

  return (
    <div className="joker-status">
      <div className={`joker-badge${unlocked ? ' active' : ''}`}>
        {unlocked ? '🃏 జోకర్ చేతన్నది' : '🔒 జోకర్ లాక్'}
      </div>
      {unlocked && (
        <div style={{ fontSize: '0.58rem', color: 'rgba(255,215,0,0.75)', textAlign: 'center', marginTop: 2 }}>
          అడవి పేక వాడవచ్చు
        </div>
      )}
    </div>
  );
}

