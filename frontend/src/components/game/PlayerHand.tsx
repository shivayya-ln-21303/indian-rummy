import { useGameStore } from '../../store/gameStore';
import CardComponent from './CardComponent';
import type { Card } from '../../types/game.types';

export default function PlayerHand() {
  const {
    gameState,
    playerId,
    selectedCards,
    pendingGroups,
    toggleCardSelection,
    rearrangeCards,
    discardCard,
    declareWin,
  } = useGameStore();

  if (!gameState) return null;

  const isMyTurn = gameState.currentPlayerId === playerId;
  const hasDrawn  = gameState.myCards.length === 14;
  const myCards   = gameState.myCards;

  const groupedCardIds = new Set(pendingGroups.flat().map((c) => c.cardId));

  const addSelectionToGroup = () => {
    const selected = myCards.filter((c) => selectedCards.includes(c.cardId));
    if (selected.length < 3) return;
    const newGroups = [...pendingGroups, selected];
    rearrangeCards(newGroups);
    useGameStore.getState().clearSelection();
  };

  const removeGroup = (idx: number) => {
    const newGroups = pendingGroups.filter((_, i) => i !== idx);
    rearrangeCards(newGroups);
  };

  const handleDiscardSelected = () => {
    if (selectedCards.length === 1) {
      discardCard(selectedCards[0]);
    }
  };

  const handleDeclareWin = () => {
    const allGroupCards = pendingGroups.flat();
    if (allGroupCards.length === 13) {
      declareWin(pendingGroups);
    } else if (hasDrawn && allGroupCards.length === 13) {
      const ungrouped14 = myCards.find((c) => !allGroupCards.find((g) => g.cardId === c.cardId));
      declareWin(pendingGroups, ungrouped14?.cardId);
    }
  };

  const canDeclareWin = () => {
    const allGroupCards = pendingGroups.flat();
    const totalNeeded = hasDrawn ? 14 : 13;
    return (
      pendingGroups.length === 4 &&
      (allGroupCards.length === 13 || allGroupCards.length === totalNeeded - 1)
    );
  };

  return (
    <div className="player-hand-container">
      {/* Groups area */}
      {pendingGroups.length > 0 && (
        <div className="groups-area">
          {pendingGroups.map((group, gi) => (
            <div key={gi} className="group-row">
              <span className="group-label">G{gi + 1}</span>
              {group.map((card) => (
                <CardComponent key={card.cardId} card={card} inGroup />
              ))}
              <button
                style={{ background: 'none', border: 'none', color: '#e63946', fontSize: '1.2rem', cursor: 'pointer', marginLeft: 6, padding: '0 4px' }}
                onClick={() => removeGroup(gi)}
                title="సమూహం తొలగించు"
              >
                ✕
              </button>
            </div>
          ))}
        </div>
      )}

      {/* Selection action bar */}
      {selectedCards.length > 0 && (
        <div className="selection-bar">
          {selectedCards.length >= 3 && (
            <button className="btn btn-primary btn-sm" onClick={addSelectionToGroup}>
              సమూహం ({selectedCards.length}) చేయండి
            </button>
          )}
          {selectedCards.length === 1 && isMyTurn && hasDrawn && (
            <button className="btn btn-danger btn-sm" onClick={handleDiscardSelected}>
              🗑️ పేక వేయండి
            </button>
          )}
          <button className="btn btn-secondary btn-sm" onClick={() => useGameStore.getState().clearSelection()}>
            రద్దు
          </button>
          {canDeclareWin() && (
            <button
              className="btn btn-primary btn-sm"
              style={{ background: 'linear-gradient(135deg,#ffd700,#c8a200)', color: '#000' }}
              onClick={handleDeclareWin}
            >
              🏆 గెలుపు ప్రకటించండి
            </button>
          )}
        </div>
      )}

      {/* Declare win button when all groups ready and nothing selected */}
      {canDeclareWin() && selectedCards.length === 0 && (
        <div style={{ display: 'flex', padding: '3px 12px' }}>
          <button
            className="btn btn-primary btn-sm"
            style={{ background: 'linear-gradient(135deg,#ffd700,#c8a200)', color: '#000', flex: 1 }}
            onClick={handleDeclareWin}
          >
            🏆 గెలుపు ప్రకటించండి!
          </button>
        </div>
      )}

      <div className="my-label">మీ పేకలు ({myCards.length})</div>
      <div className="hand-scroll">
        {myCards.map((card) => (
          <CardComponent
            key={card.cardId}
            card={card}
            selected={selectedCards.includes(card.cardId)}
            inGroup={groupedCardIds.has(card.cardId)}
            onClick={() => toggleCardSelection(card.cardId)}
          />
        ))}
      </div>
    </div>
  );
}
