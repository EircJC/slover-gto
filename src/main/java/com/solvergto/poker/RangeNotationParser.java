package com.solvergto.poker;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

public final class RangeNotationParser {
    private static final String RANKS = "23456789TJQKA";
    private static final char[] SUITS = {'s', 'h', 'd', 'c'};

    private RangeNotationParser() {
    }

    public static List<String> parse(String rangeText) {
        Objects.requireNonNull(rangeText, "rangeText");
        Set<String> combos = new LinkedHashSet<>();
        for (String rawToken : rangeText.split(",")) {
            String token = rawToken.trim().replace(" ", "");
            if (token.isEmpty()) {
                continue;
            }
            combos.addAll(parseToken(token));
        }
        if (combos.isEmpty()) {
            throw new IllegalArgumentException("Range cannot be empty");
        }
        return new ArrayList<>(combos);
    }

    private static List<String> parseToken(String token) {
        if (token.length() == 4 && isSuit(token.charAt(1)) && isSuit(token.charAt(3))) {
            return List.of(normalizeExactCombo(token));
        }

        boolean plus = token.endsWith("+");
        String base = plus ? token.substring(0, token.length() - 1) : token;
        if (base.length() < 2 || base.length() > 3) {
            throw new IllegalArgumentException("Unsupported range token: " + token);
        }

        char firstRank = normalizeRank(base.charAt(0));
        char secondRank = normalizeRank(base.charAt(1));
        char highRank = firstRank;
        char lowRank = secondRank;
        if (rankIndex(highRank) < rankIndex(lowRank)) {
            highRank = secondRank;
            lowRank = firstRank;
        }

        if (highRank == lowRank) {
            return plus ? expandPairPlus(highRank) : pairCombos(highRank);
        }

        Character suitedness = base.length() == 3 ? Character.toLowerCase(base.charAt(2)) : null;
        if (suitedness != null && suitedness != 's' && suitedness != 'o') {
            throw new IllegalArgumentException("Unsupported suitedness token: " + token);
        }

        if (plus) {
            if (isConnector(highRank, lowRank)) {
                return expandConnectorPlus(highRank, suitedness);
            }
            return expandNonPairPlus(highRank, lowRank, suitedness);
        }
        return handClassCombos(highRank, lowRank, suitedness);
    }

    private static List<String> expandPairPlus(char pairRank) {
        List<String> out = new ArrayList<>();
        for (int index = rankIndex(pairRank); index < RANKS.length(); index++) {
            out.addAll(pairCombos(RANKS.charAt(index)));
        }
        return out;
    }

    private static List<String> expandNonPairPlus(char highRank, char lowRank, Character suitedness) {
        List<String> out = new ArrayList<>();
        int highIndex = rankIndex(highRank);
        int lowIndex = rankIndex(lowRank);
        for (int index = lowIndex; index < highIndex; index++) {
            out.addAll(handClassCombos(highRank, RANKS.charAt(index), suitedness));
        }
        return out;
    }

    private static List<String> expandConnectorPlus(char highRank, Character suitedness) {
        List<String> out = new ArrayList<>();
        int highIndex = rankIndex(highRank);
        for (int index = highIndex; index < RANKS.length(); index++) {
            if (index == 0) {
                continue;
            }
            out.addAll(handClassCombos(RANKS.charAt(index), RANKS.charAt(index - 1), suitedness));
        }
        return out;
    }

    private static List<String> handClassCombos(char highRank, char lowRank, Character suitedness) {
        List<String> out = new ArrayList<>();
        if (suitedness == null || suitedness == 's') {
            out.addAll(suitedCombos(highRank, lowRank));
        }
        if (suitedness == null || suitedness == 'o') {
            out.addAll(offsuitCombos(highRank, lowRank));
        }
        return out;
    }

    private static List<String> pairCombos(char rank) {
        List<String> out = new ArrayList<>();
        for (int i = 0; i < SUITS.length; i++) {
            for (int j = i + 1; j < SUITS.length; j++) {
                out.add("" + rank + SUITS[i] + rank + SUITS[j]);
            }
        }
        return out;
    }

    private static List<String> suitedCombos(char highRank, char lowRank) {
        List<String> out = new ArrayList<>();
        for (char suit : SUITS) {
            out.add("" + highRank + suit + lowRank + suit);
        }
        return out;
    }

    private static List<String> offsuitCombos(char highRank, char lowRank) {
        List<String> out = new ArrayList<>();
        for (char highSuit : SUITS) {
            for (char lowSuit : SUITS) {
                if (highSuit == lowSuit) {
                    continue;
                }
                out.add("" + highRank + highSuit + lowRank + lowSuit);
            }
        }
        return out;
    }

    private static String normalizeExactCombo(String token) {
        String normalized = token.toUpperCase(Locale.ROOT);
        char firstSuit = Character.toLowerCase(normalized.charAt(1));
        char secondSuit = Character.toLowerCase(normalized.charAt(3));
        return "" + normalizeRank(normalized.charAt(0)) + firstSuit
                + normalizeRank(normalized.charAt(2)) + secondSuit;
    }

    private static char normalizeRank(char rank) {
        char normalized = Character.toUpperCase(rank);
        if (RANKS.indexOf(normalized) < 0) {
            throw new IllegalArgumentException("Unsupported rank: " + rank);
        }
        return normalized;
    }

    private static int rankIndex(char rank) {
        return RANKS.indexOf(rank);
    }

    private static boolean isConnector(char highRank, char lowRank) {
        return rankIndex(highRank) - rankIndex(lowRank) == 1;
    }

    private static boolean isSuit(char suit) {
        return "shdcSHDC".indexOf(suit) >= 0;
    }
}
