import { useGameStore } from '../../store/gameStore';

export default function JokerStatus() {
  const { gameState } = useGameStore();
  if (!gameState) return null;

  const unlocked = gameState.jokerUnlocked;

  return (
    <div className="joker-status">
      <div className={`joker-badge${unlocked ? ' active' : ''}`}>
        {unlocked ? '🃏 Joker Active' : '🃏 Locked'}
      </div>
      {unlocked && (
        <div style={{ fontSize: '0.55rem', color: 'rgba(255,215,0,0.7)', textAlign: 'center', marginTop: 2 }}>
          Wild card enabled
        </div>
      )}
    </div>
  );
}

