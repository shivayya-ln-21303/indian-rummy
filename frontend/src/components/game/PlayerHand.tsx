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

  // Track which cards are already in groups
  const groupedCardIds = new Set(pendingGroups.flat().map((c) => c.cardId));

  // Build ungrouped hand cards (those not yet placed in a group)
  const ungroupedCards = myCards.filter((c) => !groupedCardIds.has(c.cardId));

  // Add selected cards to a new group
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
    // All 13 cards must be in groups covering all cards
    const allGroupCards = pendingGroups.flat();
    if (allGroupCards.length === 13) {
      declareWin(pendingGroups);
    } else if (hasDrawn && allGroupCards.length === 13) {
      // 14th card is the one not in any group
      const ungrouped14 = myCards.find((c) => !allGroupCards.find((g) => g.cardId === c.cardId));
      declareWin(pendingGroups, ungrouped14?.cardId);
    }
  };

  const canDeclareWin = () => {
    const allGroupCards = pendingGroups.flat();
    const totalNeeded = hasDrawn ? 14 : 13;
    // Need groups covering 13 cards, plus optionally 1 discard card
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
                style={{ background: 'none', border: 'none', color: '#e63946', fontSize: '1rem', cursor: 'pointer', marginLeft: 4 }}
                onClick={() => removeGroup(gi)}
              >
                ✕
              </button>
            </div>
          ))}
        </div>
      )}

      {/* Action row for selected cards */}
      {selectedCards.length > 0 && (
        <div style={{ display: 'flex', gap: 6, padding: '4px 12px', flexWrap: 'wrap' }}>
          {selectedCards.length >= 3 && (
            <button className="btn btn-primary btn-sm" onClick={addSelectionToGroup}>
              Group ({selectedCards.length})
            </button>
          )}
          {selectedCards.length === 1 && isMyTurn && hasDrawn && (
            <button className="btn btn-danger btn-sm" onClick={handleDiscardSelected}>
              Discard
            </button>
          )}
          <button className="btn btn-secondary btn-sm" onClick={() => useGameStore.getState().clearSelection()}>
            Clear
          </button>
          {canDeclareWin() && (
            <button className="btn btn-primary btn-sm" style={{ background: '#ffd700', color: '#000' }} onClick={handleDeclareWin}>
              🏆 Declare Win
            </button>
          )}
        </div>
      )}

      <div className="my-label">YOUR HAND ({myCards.length})</div>
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

