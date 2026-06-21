import type { Card as CardType } from '../../types/game.types';
import { RANK_DISPLAY, SUIT_SYMBOL, isRedSuit } from '../../types/game.types';

interface CardProps {
  card: CardType;
  selected?: boolean;
  inGroup?: boolean;
  faceDown?: boolean;
  small?: boolean;
  onClick?: () => void;
}

export default function CardComponent({ card, selected, inGroup, faceDown, small, onClick }: CardProps) {
  if (faceDown) {
    return (
      <div
        className="card back"
        style={small ? { '--card-w': '22px', '--card-h': '32px' } as React.CSSProperties : undefined}
      />
    );
  }

  if (card.joker) {
    return (
      <div className={`card joker-card${selected ? ' selected' : ''}`} onClick={onClick}>
        <div className="card-corner" style={{ fontSize: '1.4rem' }}>🃏</div>
        <div style={{ fontSize: '0.55rem', color: '#ffd700', fontWeight: 700 }}>JOKER</div>
        <div className="card-corner bottom" style={{ fontSize: '1.4rem' }}>🃏</div>
      </div>
    );
  }

  const rankDisplay = RANK_DISPLAY[card.rank];
  const suitSymbol  = SUIT_SYMBOL[card.suit];
  const colorClass  = isRedSuit(card.suit) ? 'red' : 'black';

  return (
    <div
      className={`card ${colorClass}${selected ? ' selected' : ''}${inGroup ? ' in-group' : ''}`}
      onClick={onClick}
    >
      <div className="card-corner">
        <div className="card-rank">{rankDisplay}</div>
        <div className="card-suit">{suitSymbol}</div>
      </div>
      <div className="card-center">{suitSymbol}</div>
      <div className="card-corner bottom">
        <div className="card-rank">{rankDisplay}</div>
        <div className="card-suit">{suitSymbol}</div>
      </div>
    </div>
  );
}

