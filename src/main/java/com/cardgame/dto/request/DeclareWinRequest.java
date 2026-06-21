package com.cardgame.dto.request;

import java.util.List;

/**
 * DECLARE_WIN payload.
 * The player must submit exactly 4 groups covering all 13 cards in their hand
 * (one group of 4, three groups of 3).
 * If the player has drawn but not discarded (hand = 14 cards),
 * they also specify which single card to discard via {@code discardCardId}.
 *
 * <pre>
 * {
 *   "groups":       [ [...3 cardIds...], [...3 cardIds...], [...3 cardIds...], [...4 cardIds...] ],
 *   "discardCardId": "H-2-1"   // only if hand currently has 14 cards
 * }
 * </pre>
 */
public record DeclareWinRequest(List<List<String>> groups, String discardCardId) {}

