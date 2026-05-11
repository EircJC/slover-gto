package com.solvergto.poker;

import java.util.Objects;

public record Card(int rank, char suit) {
    public Card {
        if (rank < 2 || rank > 14) {
            throw new IllegalArgumentException("Invalid rank: " + rank);
        }
        if ("shdcSHDC".indexOf(suit) < 0) {
            throw new IllegalArgumentException("Invalid suit: " + suit);
        }
    }

    public static Card parse(String text) {
        Objects.requireNonNull(text, "text");
        if (text.length() != 2) {
            throw new IllegalArgumentException("Card must be 2 chars: " + text);
        }
        return new Card(parseRank(text.charAt(0)), Character.toLowerCase(text.charAt(1)));
    }

    private static int parseRank(char rank) {
        return switch (Character.toUpperCase(rank)) {
            case '2' -> 2;
            case '3' -> 3;
            case '4' -> 4;
            case '5' -> 5;
            case '6' -> 6;
            case '7' -> 7;
            case '8' -> 8;
            case '9' -> 9;
            case 'T' -> 10;
            case 'J' -> 11;
            case 'Q' -> 12;
            case 'K' -> 13;
            case 'A' -> 14;
            default -> throw new IllegalArgumentException("Invalid rank: " + rank);
        };
    }

    public String text() {
        return rankText(rank) + suit;
    }

    public static String rankText(int rank) {
        return switch (rank) {
            case 2 -> "2";
            case 3 -> "3";
            case 4 -> "4";
            case 5 -> "5";
            case 6 -> "6";
            case 7 -> "7";
            case 8 -> "8";
            case 9 -> "9";
            case 10 -> "T";
            case 11 -> "J";
            case 12 -> "Q";
            case 13 -> "K";
            case 14 -> "A";
            default -> throw new IllegalArgumentException("Invalid rank: " + rank);
        };
    }
}
