package com.cardgame.dto.request;

import java.util.List;

/**
 * REARRANGE_CARDS payload — player's current grouping of their hand.
 * Each inner list is one proposed group of card IDs.
 * Server stores this but does NOT validate until DECLARE_WIN.
 * <pre>
 * { "groups": [["H-5-1","D-5-2","C-5-1"], ["H-A-1","D-A-1","C-A-2","S-A-2"], ...] }
 * </pre>
 */
public record RearrangeCardsRequest(List<List<String>> groups) {}

