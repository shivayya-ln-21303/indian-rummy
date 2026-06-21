package com.cardgame.service;

import com.cardgame.exception.InvalidMoveException;
import com.cardgame.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Pure game-logic engine for Indian Rummy.
 *
 * <h3>Game Rules Implemented</h3>
 * <ul>
 *   <li>105 cards (2 × 52 + 1 Joker), dealt 13 to each player in round-robin order.</li>
 *   <li>On each turn: draw from deck OR draw top discard, then discard one card.</li>
 *   <li>Win condition: one group of 4 same-rank cards + three groups of 3 same-rank cards.</li>
 *   <li>Joker unlocks when the 4-card group is validated — it can then substitute in 3-card groups.</li>
 * </ul>
 *
 * <h3>Thread-safety</h3>
 * Every public method acquires the room's {@link java.util.concurrent.locks.ReentrantLock}.
 */
@Service
public class GameEngine {

    private static final Logger log = LoggerFactory.getLogger(GameEngine.class);

    @Value("${game.cards-per-player:13}")
    private int cardsPerPlayer;

    // ===========================================================================
    // DEALING
    // ===========================================================================

    /**
     * Shuffles and deals {@code cardsPerPlayer} cards to each player in strict
     * round-robin order (P1→P2→P3→P4→P1→…).
     * Places one card face-up on the discard pile.
     * Remaining cards form the draw pile.
     *
     * @return Map of playerId → list of cards dealt (for CARD_DISTRIBUTED events).
     */
    public Map<String, List<Card>> dealCards(GameRoom room) {
        room.getLock().lock();
        try {
            room.getDeck().shuffle();

            Map<String, List<Card>> dealt = new LinkedHashMap<>();
            for (Player p : room.getPlayers()) {
                dealt.put(p.getPlayerId(), new ArrayList<>());
                p.getHandCards().clear();
            }

            List<Player> playerList = new ArrayList<>(room.getPlayers());
            int totalCards = cardsPerPlayer * playerList.size();

            // Round-robin deal
            for (int i = 0; i < totalCards; i++) {
                Player target = playerList.get(i % playerList.size());
                room.getDeck().draw().ifPresent(card -> {
                    target.getHandCards().add(card);
                    dealt.get(target.getPlayerId()).add(card);
                });
            }

            // Place first remaining card face-up on discard pile
            room.getDeck().draw().ifPresent(card -> room.getDiscardPile().push(card));

            room.setCurrentTurnIndex(0);
            room.setCurrentPlayerHasDrawn(false);
            room.setStatus(RoomStatus.PLAYING);

            log.info("Room [{}] — dealt {} cards per player, deck has {} remaining",
                    room.getRoomCode(), cardsPerPlayer, room.getDeck().size());
            return dealt;
        } finally {
            room.getLock().unlock();
        }
    }

    // ===========================================================================
    // DRAW FROM DECK
    // ===========================================================================

    /**
     * Draws the top card from the deck and adds it to the player's hand.
     *
     * @return the drawn card (only shown to the drawing player).
     */
    public Card drawFromDeck(GameRoom room, String playerId) {
        room.getLock().lock();
        try {
            validateTurnAndState(room, playerId);
            if (room.isCurrentPlayerHasDrawn()) {
                throw new InvalidMoveException("You have already drawn this turn; discard or declare win first.");
            }
            if (room.getDeck().isEmpty()) {
                throw new InvalidMoveException("Draw pile is empty.");
            }

            Card drawn = room.getDeck().draw().orElseThrow();
            Player player = room.findByPlayerId(playerId).orElseThrow();
            player.getHandCards().add(drawn);
            room.setCurrentPlayerHasDrawn(true);
            room.setLastActivityTime(java.time.LocalDateTime.now());

            log.info("Room [{}] — {} drew from deck (hand={})", room.getRoomCode(), player.getPlayerName(), player.handSize());
            return drawn;
        } finally {
            room.getLock().unlock();
        }
    }

    // ===========================================================================
    // DRAW FROM DISCARD
    // ===========================================================================

    /**
     * Takes the top card from the discard pile and adds it to the player's hand.
     *
     * @return the taken card.
     */
    public Card drawFromDiscard(GameRoom room, String playerId) {
        room.getLock().lock();
        try {
            validateTurnAndState(room, playerId);
            if (room.isCurrentPlayerHasDrawn()) {
                throw new InvalidMoveException("You have already drawn this turn; discard or declare win first.");
            }
            if (room.getDiscardPile().isEmpty()) {
                throw new InvalidMoveException("Discard pile is empty.");
            }

            Card taken = room.getDiscardPile().pop();
            Player player = room.findByPlayerId(playerId).orElseThrow();
            player.getHandCards().add(taken);
            room.setCurrentPlayerHasDrawn(true);
            room.setLastActivityTime(java.time.LocalDateTime.now());

            log.info("Room [{}] — {} drew from discard: {}", room.getRoomCode(), player.getPlayerName(), taken);
            return taken;
        } finally {
            room.getLock().unlock();
        }
    }

    // ===========================================================================
    // DISCARD
    // ===========================================================================

    /**
     * Discards one card from the player's hand onto the top of the discard pile
     * and advances the turn.
     *
     * @return the discarded card.
     */
    public Card discardCard(GameRoom room, String playerId, String cardId) {
        room.getLock().lock();
        try {
            validateTurnAndState(room, playerId);
            if (!room.isCurrentPlayerHasDrawn()) {
                throw new InvalidMoveException("You must draw a card before discarding.");
            }

            Player player = room.findByPlayerId(playerId).orElseThrow();
            Card card = player.findCard(cardId);
            if (card == null) {
                throw new InvalidMoveException("Card not found in your hand: " + cardId);
            }

            player.getHandCards().remove(card);
            room.getDiscardPile().push(card);
            room.advanceTurn();

            log.info("Room [{}] — {} discarded {}", room.getRoomCode(), player.getPlayerName(), card);
            return card;
        } finally {
            room.getLock().unlock();
        }
    }

    // ===========================================================================
    // REARRANGE CARDS (store player's grouping)
    // ===========================================================================

    /**
     * Stores the player's current card grouping proposal.
     * No validation is performed — this is purely an organisational action.
     * Validation happens only on {@link #declareWin}.
     */
    public void rearrangeCards(GameRoom room, String playerId, List<List<String>> groupIds) {
        room.getLock().lock();
        try {
            validateActive(room);
            Player player = room.findByPlayerId(playerId)
                    .orElseThrow(() -> new InvalidMoveException("Player not found."));

            // Resolve card IDs → Card objects
            Map<String, Card> cardMap = player.getHandCards().stream()
                    .collect(Collectors.toMap(Card::cardId, c -> c));

            player.getGroups().clear();
            for (List<String> idGroup : groupIds) {
                List<Card> group = new ArrayList<>();
                for (String id : idGroup) {
                    Card c = cardMap.get(id);
                    if (c != null) group.add(c);
                }
                player.getGroups().add(group);
            }
        } finally {
            room.getLock().unlock();
        }
    }

    // ===========================================================================
    // DECLARE WIN
    // ===========================================================================

    /**
     * Validates a player's win declaration.
     *
     * <h3>Validation algorithm</h3>
     * <ol>
     *   <li>If hand = 14 (player drew but hasn't discarded), {@code discardCardId} is removed first.</li>
     *   <li>Exactly 4 groups must be provided covering all 13 hand cards.</li>
     *   <li>One group must have exactly 4 cards of the same rank (the "four-of-a-kind" group).</li>
     *   <li>The four-of-a-kind group unlocks the Joker globally.</li>
     *   <li>The remaining 3 groups must each have exactly 3 cards of the same rank,
     *       or 2 same-rank cards + the Joker (if joker is now unlocked).</li>
     * </ol>
     *
     * @return {@code true} if the declaration is valid (player wins).
     */
    public boolean declareWin(GameRoom room, String playerId,
                              List<List<String>> groupIds, String discardCardId) {
        room.getLock().lock();
        try {
            validateActive(room);
            Player player = room.findByPlayerId(playerId)
                    .orElseThrow(() -> new InvalidMoveException("Player not in this room."));

            // --- Step 1: handle 14-card hand ---
            if (player.handSize() == 14) {
                if (discardCardId == null || discardCardId.isBlank()) {
                    throw new InvalidMoveException(
                            "You have 14 cards. Specify discardCardId to remove before declaring win.");
                }
                Card toDiscard = player.findCard(discardCardId);
                if (toDiscard == null) {
                    throw new InvalidMoveException("discardCardId not in your hand: " + discardCardId);
                }
                player.getHandCards().remove(toDiscard);
                room.getDiscardPile().push(toDiscard);
            }

            if (player.handSize() != 13) {
                throw new InvalidMoveException("Hand must have exactly 13 cards to declare win.");
            }

            // --- Step 2: resolve group card IDs → Card objects ---
            Map<String, Card> cardMap = player.getHandCards().stream()
                    .collect(Collectors.toMap(Card::cardId, c -> c));

            if (groupIds.size() != 4) {
                throw new InvalidMoveException("Exactly 4 groups are required (1×4 + 3×3).");
            }

            List<List<Card>> resolvedGroups = new ArrayList<>();
            Set<String> seen = new HashSet<>();
            for (List<String> idGroup : groupIds) {
                List<Card> group = new ArrayList<>();
                for (String id : idGroup) {
                    if (!cardMap.containsKey(id)) {
                        throw new InvalidMoveException("Card " + id + " is not in your hand.");
                    }
                    if (!seen.add(id)) {
                        throw new InvalidMoveException("Duplicate card in groups: " + id);
                    }
                    group.add(cardMap.get(id));
                }
                resolvedGroups.add(group);
            }
            if (seen.size() != 13) {
                throw new InvalidMoveException("Groups must cover exactly all 13 hand cards.");
            }

            // --- Step 3: find and validate the 4-card group ---
            List<Card> fourGroup = null;
            List<List<Card>> threeGroups = new ArrayList<>();

            for (List<Card> g : resolvedGroups) {
                if (g.size() == 4) {
                    if (fourGroup != null) {
                        throw new InvalidMoveException("Only one group of 4 is allowed.");
                    }
                    fourGroup = g;
                } else if (g.size() == 3) {
                    threeGroups.add(g);
                } else {
                    throw new InvalidMoveException(
                            "Invalid group size " + g.size() + ". Groups must be 3 or 4.");
                }
            }

            if (fourGroup == null) {
                throw new InvalidMoveException("You must have a group of exactly 4 same-rank cards.");
            }
            if (threeGroups.size() != 3) {
                throw new InvalidMoveException("You must have exactly 3 groups of 3 cards.");
            }

            // Validate 4-card group (must be pure — no Joker substitution allowed)
            validatePureGroup(fourGroup);

            // Joker unlocks at this point
            boolean jokerJustUnlocked = !room.isJokerUnlocked();
            room.setJokerUnlocked(true);
            if (room.getStatus() == RoomStatus.PLAYING) {
                room.setStatus(RoomStatus.JOKER_UNLOCKED);
            }

            // --- Step 4: validate three 3-card groups ---
            for (List<Card> g : threeGroups) {
                validateThreeGroup(g, true /* joker is now unlocked */);
            }

            // --- WIN! ---
            room.setStatus(RoomStatus.FINISHED);
            room.setWinnerId(playerId);
            room.setWinnerName(player.getPlayerName());
            log.info("Room [{}] — WINNER: {} (joker was {})",
                    room.getRoomCode(), player.getPlayerName(),
                    jokerJustUnlocked ? "just unlocked" : "already unlocked");
            return true;

        } finally {
            room.getLock().unlock();
        }
    }

    // ===========================================================================
    // AUTO TURN ADVANCE (called by timer service)
    // ===========================================================================

    /**
     * Automatically draws and discards for a timed-out player, then advances the turn.
     */
    public Card autoAdvanceTurn(GameRoom room) {
        room.getLock().lock();
        try {
            if (!room.isActive()) return null;

            Player current = room.currentPlayer().orElse(null);
            if (current == null) return null;

            // Auto-draw if not yet drawn
            if (!room.isCurrentPlayerHasDrawn() && !room.getDeck().isEmpty()) {
                Card drawn = room.getDeck().draw().orElseThrow();
                current.getHandCards().add(drawn);
                room.setCurrentPlayerHasDrawn(true);
            }

            // Auto-discard first card
            Card discarded = null;
            if (!current.getHandCards().isEmpty()) {
                discarded = current.getHandCards().remove(0);
                room.getDiscardPile().push(discarded);
            }

            room.advanceTurn();
            log.info("Room [{}] — turn timeout for {}, auto-discarded {}",
                    room.getRoomCode(), current.getPlayerName(), discarded);
            return discarded;
        } finally {
            room.getLock().unlock();
        }
    }

    // ===========================================================================
    // Validation helpers
    // ===========================================================================

    private void validateTurnAndState(GameRoom room, String playerId) {
        validateActive(room);
        Player current = room.currentPlayer()
                .orElseThrow(() -> new InvalidMoveException("No current player."));
        if (!current.getPlayerId().equals(playerId)) {
            throw new InvalidMoveException("It is not your turn (current: " + current.getPlayerName() + ").");
        }
    }

    private void validateActive(GameRoom room) {
        if (room.getStatus() != RoomStatus.PLAYING && room.getStatus() != RoomStatus.JOKER_UNLOCKED) {
            throw new InvalidMoveException("Game is not in progress (status: " + room.getStatus() + ").");
        }
    }

    /**
     * Validates a "pure" group: all cards must have the same rank; no Joker allowed.
     */
    private void validatePureGroup(List<Card> group) {
        for (Card c : group) {
            if (c.isJoker()) {
                throw new InvalidMoveException("The 4-card group cannot contain the Joker.");
            }
        }
        Rank target = group.get(0).rank();
        for (Card c : group) {
            if (c.rank() != target) {
                throw new InvalidMoveException(
                        "4-card group is invalid: mixed ranks (" + c + " vs " + target + ").");
            }
        }
    }

    /**
     * Validates a 3-card group.
     * Rules:
     * <ul>
     *   <li>All 3 same rank (pure), OR</li>
     *   <li>2 same rank + 1 Joker (impure, only when {@code jokerUnlocked = true}).</li>
     * </ul>
     */
    private void validateThreeGroup(List<Card> group, boolean jokerUnlocked) {
        long jokerCount = group.stream().filter(Card::isJoker).count();

        if (jokerCount > 0 && !jokerUnlocked) {
            throw new InvalidMoveException("Joker cannot be used — no 4-card set has been formed yet.");
        }
        if (jokerCount > 1) {
            throw new InvalidMoveException("At most 1 Joker allowed per group.");
        }

        List<Card> nonJokers = group.stream().filter(c -> !c.isJoker()).toList();

        if (nonJokers.isEmpty()) {
            throw new InvalidMoveException("A group must have at least 1 non-Joker card.");
        }

        Rank target = nonJokers.get(0).rank();
        for (Card c : nonJokers) {
            if (c.rank() != target) {
                throw new InvalidMoveException(
                        "3-card group invalid: mixed ranks (" + c + " vs " + target + ").");
            }
        }
    }

    // ===========================================================================
    // Public utility
    // ===========================================================================

    /** Returns true if a given set of cards forms a valid pure group (same rank, no Joker). */
    public boolean isPureSet(List<Card> cards) {
        if (cards.isEmpty()) return false;
        if (cards.stream().anyMatch(Card::isJoker)) return false;
        Rank r = cards.get(0).rank();
        return cards.stream().allMatch(c -> c.rank() == r);
    }
}

