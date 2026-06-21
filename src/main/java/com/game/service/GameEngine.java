package com.game.service;

import com.game.exception.InvalidMoveException;
import com.game.model.Card;
import com.game.model.GameRoom;
import com.game.model.Player;
import com.game.model.RoomState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Pure game-logic service — the authoritative engine for all in-game actions.
 *
 * <h3>Rules (Discard-Match variant)</h3>
 * <ul>
 *   <li>Each player receives {@code initialHandSize} cards at game start.</li>
 *   <li>One card is placed face-up on the discard pile.</li>
 *   <li>On your turn you must play a card that matches the top of the discard
 *       pile by <em>suit</em> OR <em>rank</em>, OR draw one card from the deck.</li>
 *   <li>Drawing always ends your turn.</li>
 *   <li>The first player to empty their hand wins.</li>
 *   <li>If the draw pile is exhausted, the discard pile (minus the top card) is
 *       reshuffled and becomes the new draw pile.</li>
 * </ul>
 *
 * <h3>Thread-safety</h3>
 * Every public method acquires the room's {@link java.util.concurrent.locks.ReentrantLock}
 * before reading or mutating state, so callers do not need to take the lock themselves.
 */
@Service
public class GameEngine {

    private static final Logger log = LoggerFactory.getLogger(GameEngine.class);

    @Value("${game.initial-hand-size:7}")
    private int initialHandSize;

    // ---------------------------------------------------------------------------
    // Game start
    // ---------------------------------------------------------------------------

    /**
     * Shuffles the deck, deals {@code initialHandSize} cards to each player,
     * and places one card on the discard pile.
     * Precondition: room state is already STARTED (set by RoomManager).
     */
    public void dealCards(GameRoom room) {
        room.getLock().lock();
        try {
            room.getDeck().shuffle();

            // Deal to each player in turn order
            for (Player player : room.getPlayers()) {
                player.getHandCards().clear();
                for (int i = 0; i < initialHandSize; i++) {
                    room.getDeck().draw().ifPresent(player.getHandCards()::add);
                }
                log.debug("Dealt {} cards to player [{}]", player.handSize(), player.getPlayerName());
            }

            // Place one card on the discard pile to start the game
            room.getDeck().draw().ifPresent(card -> {
                room.getDiscardPile().addLast(card);
                log.info("Room [{}] discard pile started with: {}", room.getRoomId(), card);
            });

            room.setCurrentTurnIndex(0);
            room.setLastActivityTime(LocalDateTime.now());
            log.info("Cards dealt in room [{}]", room.getRoomId());

        } finally {
            room.getLock().unlock();
        }
    }

    // ---------------------------------------------------------------------------
    // Play a card
    // ---------------------------------------------------------------------------

    /**
     * Validates and executes a PLAY_CARD action.
     *
     * @param room      The game room.
     * @param playerId  The acting player's stable ID.
     * @param cardIndex Zero-based index of the card in the player's hand.
     * @return The card that was played.
     * @throws InvalidMoveException if the move is illegal for any reason.
     */
    public Card playCard(GameRoom room, String playerId, int cardIndex) {
        room.getLock().lock();
        try {
            validateGameInProgress(room);

            Player player = requireCurrentPlayer(room, playerId);

            if (cardIndex < 0 || cardIndex >= player.handSize()) {
                throw new InvalidMoveException(
                        "Card index " + cardIndex + " is out of range (hand size: " + player.handSize() + ")");
            }

            Card card       = player.getHandCards().get(cardIndex);
            Card topDiscard = room.topDiscard()
                    .orElseThrow(() -> new InvalidMoveException("Discard pile is empty — this should not happen"));

            if (!isValidPlay(card, topDiscard)) {
                throw new InvalidMoveException(
                        card + " cannot be played on top of " + topDiscard
                        + " (must match suit or rank)");
            }

            // Commit the move
            player.getHandCards().remove(cardIndex);
            room.getDiscardPile().addLast(card);
            room.setLastActivityTime(LocalDateTime.now());

            log.info("Room [{}] player [{}] played {}", room.getRoomId(), player.getPlayerName(), card);

            // Check win condition BEFORE advancing turn
            if (player.handSize() == 0) {
                room.setRoomState(RoomState.FINISHED);
                room.setWinnerId(playerId);
                log.info("Room [{}] — player [{}] WON!", room.getRoomId(), player.getPlayerName());
            } else {
                room.advanceTurn();
            }

            return card;

        } finally {
            room.getLock().unlock();
        }
    }

    // ---------------------------------------------------------------------------
    // Draw a card
    // ---------------------------------------------------------------------------

    /**
     * Draws one card from the deck and adds it to the player's hand.
     * If the deck is empty the discard pile (minus the top card) is reshuffled.
     * Drawing always advances the turn.
     *
     * @param room     The game room.
     * @param playerId The acting player's stable ID.
     * @return The card drawn.
     * @throws InvalidMoveException if the deck and discard pile are both exhausted.
     */
    public Card drawCard(GameRoom room, String playerId) {
        room.getLock().lock();
        try {
            validateGameInProgress(room);

            Player player = requireCurrentPlayer(room, playerId);

            // Reshuffle discard into deck if deck is empty
            if (room.getDeck().isEmpty()) {
                reshuffleDeck(room);
            }

            Card drawn = room.getDeck().draw()
                    .orElseThrow(() -> new InvalidMoveException("No cards left to draw (deck and discard are both empty)"));

            player.getHandCards().add(drawn);
            room.setLastActivityTime(LocalDateTime.now());

            log.info("Room [{}] player [{}] drew a card (hand size now: {})",
                    room.getRoomId(), player.getPlayerName(), player.handSize());

            // Drawing ends the turn
            room.advanceTurn();

            return drawn;

        } finally {
            room.getLock().unlock();
        }
    }

    // ---------------------------------------------------------------------------
    // Validation helpers
    // ---------------------------------------------------------------------------

    /**
     * A card is playable if it matches the top discard by suit or by rank.
     *
     * @param card       Card the player wishes to play.
     * @param topDiscard Current top card of the discard pile.
     * @return {@code true} if the move is legal.
     */
    public boolean isValidPlay(Card card, Card topDiscard) {
        return card.suit() == topDiscard.suit()
                || card.rank() == topDiscard.rank();
    }

    /**
     * Returns the set of cards in the player's hand that can currently be played.
     * Useful for AI or UI hint systems.
     */
    public List<Card> getPlayableCards(GameRoom room, String playerId) {
        room.getLock().lock();
        try {
            Player player = room.findByPlayerId(playerId)
                    .orElseThrow(() -> new IllegalArgumentException("Player not in room"));

            Optional<Card> topDiscard = room.topDiscard();
            if (topDiscard.isEmpty()) return List.of();

            return player.getHandCards().stream()
                    .filter(c -> isValidPlay(c, topDiscard.get()))
                    .toList();
        } finally {
            room.getLock().unlock();
        }
    }

    // ---------------------------------------------------------------------------
    // Private helpers
    // ---------------------------------------------------------------------------

    /**
     * Asserts the room is in STARTED state (throws otherwise).
     */
    private void validateGameInProgress(GameRoom room) {
        if (room.getRoomState() != RoomState.STARTED) {
            throw new InvalidMoveException("Game is not in progress (state: " + room.getRoomState() + ")");
        }
    }

    /**
     * Asserts it is {@code playerId}'s turn and returns the {@link Player} object.
     */
    private Player requireCurrentPlayer(GameRoom room, String playerId) {
        Player current = room.currentPlayer()
                .orElseThrow(() -> new InvalidMoveException("No current player — room may be empty"));

        if (!current.getPlayerId().equals(playerId)) {
            throw new InvalidMoveException("It is not your turn (current turn: " + current.getPlayerName() + ")");
        }
        return current;
    }

    /**
     * Reshuffles the discard pile (except the top card) back into the draw pile.
     */
    private void reshuffleDeck(GameRoom room) {
        List<Card> toReshuffle = room.drainDiscardExceptTop();
        if (toReshuffle.isEmpty()) {
            log.warn("Room [{}] — nothing to reshuffle (discard only has 1 card)", room.getRoomId());
            return;
        }
        room.getDeck().addCards(toReshuffle);
        room.getDeck().shuffle();
        log.info("Room [{}] — reshuffled {} cards from discard pile back into deck",
                room.getRoomId(), toReshuffle.size());
    }
}

