import type { CSSProperties } from 'react';
import type { Card as CardType } from '../../types/game.types';
import { RANK_DISPLAY, SUIT_SYMBOL, isRedSuit } from '../../types/game.types';

interface CardProps {
  card: CardType;
  selected?: boolean;
  inGroup?: boolean;
  groupColor?: string;
  faceDown?: boolean;
  small?: boolean;
  animClass?: string;
  onClick?: () => void;
}

export default function CardComponent({
  card,
  selected = false,
  inGroup = false,
  groupColor,
  faceDown = false,
  small = false,
  animClass,
  onClick,
}: CardProps) {
  const sizeStyle = small
    ? ({ '--card-w': '46px', '--card-h': '66px' } as CSSProperties)
    : undefined;

  const className = [
    'card',
    selected ? 'selected' : '',
    inGroup ? 'in-group' : '',
    card.joker ? 'joker-card' : '',
    faceDown ? 'back' : '',
    small ? 'small-card' : '',
    animClass ?? '',
  ].filter(Boolean).join(' ');

  const style = {
    ...sizeStyle,
    ...(inGroup && groupColor ? ({ '--group-color': groupColor } as CSSProperties) : undefined),
  };

  if (faceDown) {
    return (
      <div className={className} style={style} onClick={onClick}>
        <div className="card-back-pattern">
          <span>♦</span>
          <span>♠</span>
          <span>♥</span>
          <span>♣</span>
        </div>
        <div className="card-back-center">రమ్మీ</div>
      </div>
    );
  }

  if (card.joker) {
    return (
      <div className={className} style={style} onClick={onClick}>
        <div className="card-tl joker-corner">
          <span className="card-rank">★</span>
          <span className="card-suit">🃏</span>
        </div>
        <div className="card-center joker-center">
          <span className="joker-star">✦</span>
          <span className="joker-icon">🃏</span>
          <span className="joker-label">జోకర్</span>
        </div>
        <div className="card-br joker-corner">
          <span className="card-rank">★</span>
          <span className="card-suit">🃏</span>
        </div>
      </div>
    );
  }

  const rankDisplay = RANK_DISPLAY[card.rank];
  const suitSymbol = SUIT_SYMBOL[card.suit];
  const color = isRedSuit(card.suit) ? 'var(--red-card)' : 'var(--black-card)';

  return (
    <div className={className} style={style} onClick={onClick}>
      <div className="card-tl" style={{ color }}>
        <span className="card-rank">{rankDisplay}</span>
        <span className="card-suit">{suitSymbol}</span>
      </div>
      <div className="card-center" style={{ color }}>
        {suitSymbol}
      </div>
      <div className="card-br" style={{ color }}>
        <span className="card-rank">{rankDisplay}</span>
        <span className="card-suit">{suitSymbol}</span>
      </div>
    </div>
  );
}
