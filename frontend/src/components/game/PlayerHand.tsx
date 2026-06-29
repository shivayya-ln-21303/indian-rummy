import { useMemo } from 'react';
import type { CSSProperties } from 'react';
import { useGameStore } from '../../store/gameStore';
import CardComponent from './CardComponent';
import type { Card } from '../../types/game.types';

const GROUP_COLORS = ['#f5c542', '#3b82f6', '#ef4444', '#22c55e'];

function groupLabel(group: Card[]) {
  const realCards = group.filter((card) => !card.joker);
  if (realCards.length >= 2 && realCards.every((card) => card.rank === realCards[0].rank)) {
    return 'అదే నంబరు';
  }
  return 'సీక్వెన్స్';
}

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
    clearSelection,
  } = useGameStore();

  if (!gameState) return null;

  const isMyTurn = gameState.currentPlayerId === playerId;
  const hasDrawn = gameState.myCards.length === 14;
  const myCards = gameState.myCards;
  const myJokerUnlocked = gameState.players.find((player) => player.playerId === playerId)?.jokerUnlocked ?? false;

  const cardGroupIndex = useMemo(() => {
    const map = new Map<string, number>();
    pendingGroups.forEach((group, groupIndex) => group.forEach((card) => map.set(card.cardId, groupIndex)));
    return map;
  }, [pendingGroups]);

  const selectedObjects = myCards.filter((card) => selectedCards.includes(card.cardId));
  const firstRealSelected = selectedObjects.find((card) => !card.joker);
  const allSameRank = selectedObjects.length >= 3
    && !!firstRealSelected
    && selectedObjects.every((card) => card.joker || card.rank === firstRealSelected.rank);

  const tapRank = selectedObjects.length === 1 && !selectedObjects[0].joker ? selectedObjects[0].rank : null;
  const sameRankInHand = tapRank
    ? myCards.filter((card) => !card.joker && card.rank === tapRank && !cardGroupIndex.has(card.cardId))
    : [];

  const addSelectionToGroup = () => {
    const selected = myCards.filter((card) => selectedCards.includes(card.cardId));
    if (selected.length < 3) return;
    rearrangeCards([...pendingGroups, selected]);
    clearSelection();
  };

  const removeGroup = (index: number) => {
    rearrangeCards(pendingGroups.filter((_, groupIndex) => groupIndex !== index));
  };

  const handleDiscardSelected = () => {
    if (selectedCards.length === 1) {
      discardCard(selectedCards[0]);
    }
  };

  const handleDeclareWin = () => {
    const grouped = pendingGroups.flat();
    if (grouped.length === 13) {
      declareWin(pendingGroups);
      return;
    }
    if (hasDrawn) {
      const extraCard = myCards.find((card) => !grouped.some((groupedCard) => groupedCard.cardId === card.cardId));
      declareWin(pendingGroups, extraCard?.cardId);
    }
  };

  const canDeclareWin = () => {
    const grouped = pendingGroups.flat();
    return pendingGroups.length === 4 && (grouped.length === 13 || (hasDrawn && grouped.length === 12));
  };

  return (
    <div className="player-hand-shell">
      <div className="hand-header premium-panel-secondary">
        <div>
          <h3>మీ పేకలు</h3>
          <p>{myCards.length} పేకలు సిద్ధంగా ఉన్నాయి</p>
        </div>
        <div className={`turn-hint-chip ${isMyTurn ? 'active' : ''}`}>
          {isMyTurn ? (hasDrawn ? 'పేక వేయండి లేదా గెలుపు ప్రకటించండి' : 'పేక తీసుకోండి') : 'సమూహాలు ముందే సిద్ధం చేసుకోండి'}
        </div>
      </div>

      {myJokerUnlocked && (
        <div className="joker-banner">
          <span>🃏</span>
          <span>జోకర్ అన్‌లాక్ — మీరు అడవి పేకను ఉపయోగించవచ్చు</span>
        </div>
      )}

      <div className="groups-area">
        <div className="groups-title-row">
          <span>📦 మీ సమూహాలు</span>
          <span>{pendingGroups.length}/4</span>
        </div>

        {pendingGroups.map((group, index) => {
          const color = GROUP_COLORS[index % GROUP_COLORS.length];
          return (
            <div key={index} className="group-block" style={{ '--group-accent': color } as CSSProperties}>
              <div className="group-block-header">
                <span className="group-badge">G{index + 1}</span>
                <span className="group-type-label">{groupLabel(group)}</span>
                <button type="button" className="group-remove-btn" onClick={() => removeGroup(index)}>✕</button>
              </div>
              <div className="group-card-row">
                {group.map((card) => (
                  <CardComponent
                    key={card.cardId}
                    card={card}
                    inGroup
                    groupColor={color}
                    small
                  />
                ))}
              </div>
            </div>
          );
        })}
      </div>

      {sameRankInHand.length >= 2 && (
        <div className="same-rank-hint premium-panel-secondary">
          <span>💡 ఈ నంబరుకు ఇంకా {sameRankInHand.length} పేకలు ఉన్నాయి</span>
          <button
            type="button"
            className="btn btn-secondary btn-sm"
            onClick={() => sameRankInHand.forEach((card) => {
              if (!selectedCards.includes(card.cardId)) {
                toggleCardSelection(card.cardId);
              }
            })}
          >
            అన్నీ ఎంచుకోండి
          </button>
        </div>
      )}

      {selectedCards.length > 0 && (
        <div className="selection-bar premium-panel-secondary">
          <span className="selection-count">{selectedCards.length} పేకలు ఎంపికయ్యాయి</span>
          <div className="selection-actions">
            {selectedCards.length >= 3 && (
              <button type="button" className={`btn ${allSameRank ? 'btn-gold' : 'btn-blue'} btn-sm`} onClick={addSelectionToGroup}>
                {allSameRank ? 'అదే నంబరు సమూహం' : 'సమూహం చేయండి'}
              </button>
            )}
            {selectedCards.length === 1 && isMyTurn && hasDrawn && (
              <button type="button" className="btn btn-danger btn-sm" onClick={handleDiscardSelected}>
                పేక వేయండి
              </button>
            )}
            <button type="button" className="btn btn-secondary btn-sm" onClick={clearSelection}>రద్దు</button>
          </div>
        </div>
      )}

      {canDeclareWin() && (
        <div className="declare-win-wrap">
          <button type="button" className="btn btn-gold btn-xl pulse-gold" onClick={handleDeclareWin}>
            🏆 గెలుపు ప్రకటించండి
          </button>
        </div>
      )}

      <div className="hand-scroll">
        {myCards.map((card) => {
          const groupIndex = cardGroupIndex.get(card.cardId);
          const highlightSameRank = tapRank && !card.joker && card.rank === tapRank && !cardGroupIndex.has(card.cardId);
          return (
            <div key={card.cardId} className={highlightSameRank ? 'same-rank-glow' : ''}>
              <CardComponent
                card={card}
                selected={selectedCards.includes(card.cardId)}
                inGroup={groupIndex !== undefined}
                groupColor={groupIndex !== undefined ? GROUP_COLORS[groupIndex % GROUP_COLORS.length] : undefined}
                onClick={() => toggleCardSelection(card.cardId)}
              />
            </div>
          );
        })}
      </div>
    </div>
  );
}
