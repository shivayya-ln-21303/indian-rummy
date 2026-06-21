import { useGameStore } from '../../store/gameStore';
import CardComponent from './CardComponent';

const GROUP_COLORS = ['#e67e22', '#2980b9', '#8e44ad', '#27ae60'];

export default function PlayerHand() {
  const {
    gameState, playerId, selectedCards, pendingGroups,
    toggleCardSelection, rearrangeCards, discardCard, declareWin,
  } = useGameStore();

  if (!gameState) return null;

  const isMyTurn = gameState.currentPlayerId === playerId;
  const hasDrawn  = gameState.myCards.length === 14;
  const myCards   = gameState.myCards;

  // Map cardId → which group index it belongs to
  const cardGroupIndex = new Map<string, number>();
  pendingGroups.forEach((g, gi) => g.forEach(c => cardGroupIndex.set(c.cardId, gi)));

  const addSelectionToGroup = () => {
    const selected = myCards.filter(c => selectedCards.includes(c.cardId));
    if (selected.length < 3) return;
    rearrangeCards([...pendingGroups, selected]);
    useGameStore.getState().clearSelection();
  };

  const removeGroup = (idx: number) => {
    rearrangeCards(pendingGroups.filter((_, i) => i !== idx));
  };

  const handleDiscardSelected = () => {
    if (selectedCards.length === 1) discardCard(selectedCards[0]);
  };

  const handleDeclareWin = () => {
    const all = pendingGroups.flat();
    if (all.length === 13) {
      declareWin(pendingGroups);
    } else if (hasDrawn) {
      const extra = myCards.find(c => !all.find(g => g.cardId === c.cardId));
      declareWin(pendingGroups, extra?.cardId);
    }
  };

  const canDeclareWin = () => {
    const all = pendingGroups.flat();
    return pendingGroups.length === 4 &&
      (all.length === 13 || (hasDrawn && all.length === 12));
  };

  /* ── Smart grouping helpers ── */
  const selectedObjs = myCards.filter(c => selectedCards.includes(c.cardId));

  // Same-rank detection (excluding joker)
  const allSameRank = selectedObjs.length >= 3 &&
    selectedObjs.every(c => !c.joker && c.rank === selectedObjs[0].rank);

  // Highlight same-rank cards in hand when user taps one card
  const tapRank = selectedObjs.length === 1 && !selectedObjs[0].joker
    ? selectedObjs[0].rank : null;
  const sameRankInHand = tapRank
    ? myCards.filter(c => !c.joker && c.rank === tapRank && !cardGroupIndex.has(c.cardId))
    : [];

  return (
    <div className="player-hand-container">

      {/* ── Groups area ── */}
      {pendingGroups.length > 0 && (
        <div className="groups-area">
          {pendingGroups.map((group, gi) => {
            const gc = GROUP_COLORS[gi % GROUP_COLORS.length];
            const sameRankGroup = group.every(c => !c.joker && c.rank === group[0].rank) ||
              (group.filter(c => !c.joker).every(c => c.rank === group.find(x => !x.joker)?.rank));
            return (
              <div key={gi} className="group-row" style={{ borderColor: `${gc}88` }}>
                <div className="group-label-box" style={{ background: gc }}>
                  G{gi + 1}
                  {sameRankGroup && <span style={{ fontSize: '0.5rem', display: 'block' }}>సంఖ్య</span>}
                </div>
                {group.map(card => (
                  <CardComponent
                    key={card.cardId}
                    card={card}
                    inGroup
                    groupColor={gc}
                  />
                ))}
                <button className="group-remove-btn" onClick={() => removeGroup(gi)}>✕</button>
              </div>
            );
          })}
        </div>
      )}

      {/* ── Same-rank hint (tap 1 card) ── */}
      {sameRankInHand.length >= 2 && (
        <div className="same-rank-hint">
          <span>💡 {sameRankInHand.length + 1} అదే నంబరు పేకలు ఉన్నాయి</span>
          <button
            className="btn btn-sm"
            style={{ background: '#f39c12', color: '#fff', border: 'none', borderRadius: 8, padding: '4px 10px', fontSize: '0.78rem', fontFamily: 'inherit', fontWeight: 700 }}
            onClick={() => {
              sameRankInHand.forEach(c => {
                if (!selectedCards.includes(c.cardId)) useGameStore.getState().toggleCardSelection(c.cardId);
              });
            }}
          >
            అన్నీ సెలెక్ట్ చేయండి
          </button>
        </div>
      )}

      {/* ── Selection action bar ── */}
      {selectedCards.length > 0 && (
        <div className="selection-bar">
          {selectedCards.length >= 3 && allSameRank && (
            <button
              className="btn btn-sm same-rank-btn"
              onClick={addSelectionToGroup}
            >
              🎯 అదే నంబరు సమూహం ({selectedCards.length})
            </button>
          )}
          {selectedCards.length >= 3 && !allSameRank && (
            <button className="btn btn-primary btn-sm" onClick={addSelectionToGroup}>
              ✅ సమూహం చేయండి ({selectedCards.length})
            </button>
          )}
          {selectedCards.length === 1 && isMyTurn && hasDrawn && (
            <button className="btn btn-danger btn-sm" onClick={handleDiscardSelected}>
              🗑️ పేక వేయండి
            </button>
          )}
          <button className="btn btn-secondary btn-sm" onClick={() => useGameStore.getState().clearSelection()}>
            ✖ రద్దు
          </button>
        </div>
      )}

      {/* ── Declare win button ── */}
      {canDeclareWin() && (
        <div style={{ padding: '4px 10px' }}>
          <button
            className="btn declare-win-btn"
            onClick={handleDeclareWin}
            style={{ width: '100%' }}
          >
            🏆 గెలుపు ప్రకటించండి!
          </button>
        </div>
      )}

      {/* ── Hand label + scroll ── */}
      <div className="my-label">
        మీ పేకలు ({myCards.length})
        {isMyTurn && !hasDrawn && <span className="turn-hint-label"> ← పేక తీసుకోండి</span>}
        {isMyTurn && hasDrawn && <span className="turn-hint-label"> → పేక వేయండి లేదా గ్రూప్ చేయండి</span>}
      </div>
      <div className="hand-scroll">
        {myCards.map(card => {
          const gi = cardGroupIndex.get(card.cardId);
          const isSameRankHighlighted = tapRank && !card.joker && card.rank === tapRank && !cardGroupIndex.has(card.cardId);
          return (
            <div
              key={card.cardId}
              className={isSameRankHighlighted ? 'same-rank-glow' : ''}
            >
              <CardComponent
                card={card}
                selected={selectedCards.includes(card.cardId)}
                inGroup={gi !== undefined}
                groupColor={gi !== undefined ? GROUP_COLORS[gi % GROUP_COLORS.length] : undefined}
                onClick={() => toggleCardSelection(card.cardId)}
              />
            </div>
          );
        })}
      </div>
    </div>
  );
}
