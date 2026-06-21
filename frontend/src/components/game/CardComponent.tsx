import type { Card as CardType } from '../../types/game.types';
import { RANK_DISPLAY, SUIT_SYMBOL, isRedSuit } from '../../types/game.types';

interface CardProps {
  card: CardType;
  selected?: boolean;
  inGroup?: boolean;
  groupColor?: string;
  faceDown?: boolean;
  small?: boolean;
  onClick?: () => void;
}

export default function CardComponent({
  card, selected, inGroup, groupColor, faceDown, small, onClick,
}: CardProps) {
  const sizeStyle = small
    ? ({ '--card-w': '28px', '--card-h': '40px' } as React.CSSProperties)
    : undefined;

  /* ── Face-down card ── */
  if (faceDown) {
    return <div className="card back" style={sizeStyle} />;
  }

  /* ── Joker ── */
  if (card.joker) {
    return (
      <div
        className={`card joker-card${selected ? ' selected' : ''}${inGroup ? ' in-group' : ''}`}
        style={groupColor && inGroup ? { borderColor: groupColor, boxShadow: `0 0 8px ${groupColor}88` } : undefined}
        onClick={onClick}
      >
        <div className="card-tl" style={{ color: '#b8860b' }}>
          <span className="card-rank-text">★</span>
        </div>
        <div className="card-joker-center">
          <span style={{ fontSize: '2rem' }}>🃏</span>
          <span className="card-joker-label">జోకర్</span>
        </div>
        <div className="card-br" style={{ color: '#b8860b' }}>
          <span className="card-rank-text">★</span>
        </div>
      </div>
    );
  }

  /* ── Regular card ── */
  const rankDisplay = RANK_DISPLAY[card.rank];
  const suitSymbol  = SUIT_SYMBOL[card.suit];
  const isRed       = isRedSuit(card.suit);
  const color       = isRed ? '#c0392b' : '#1a1a1a';

  const groupBorderStyle = groupColor && inGroup
    ? { borderColor: groupColor, borderWidth: '2.5px', boxShadow: `0 0 10px ${groupColor}66` }
    : undefined;

  return (
    <div
      className={`card${selected ? ' selected' : ''}${inGroup ? ' in-group' : ''}`}
      style={{ ...sizeStyle, ...groupBorderStyle }}
      onClick={onClick}
    >
      {/* Top-left corner */}
      <div className="card-tl" style={{ color }}>
        <span className="card-rank-text">{rankDisplay}</span>
        <span className="card-suit-text">{suitSymbol}</span>
      </div>

      {/* Center suit */}
      <div className="card-suit-center" style={{ color }}>
        {suitSymbol}
      </div>

      {/* Bottom-right corner (rotated) */}
      <div className="card-br" style={{ color }}>
        <span className="card-rank-text">{rankDisplay}</span>
        <span className="card-suit-text">{suitSymbol}</span>
      </div>
    </div>
  );
}

