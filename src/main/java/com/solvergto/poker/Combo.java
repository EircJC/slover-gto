package com.solvergto.poker;

import java.util.Objects;

public record Combo(Card first, Card second) {
    public Combo {
        Objects.requireNonNull(first, "first");
        Objects.requireNonNull(second, "second");
        if (first.equals(second)) {
            throw new IllegalArgumentException("Combo cannot contain duplicate cards");
        }
    }

    public static Combo parse(String text) {
        Objects.requireNonNull(text, "text");
        if (text.length() != 4) {
            throw new IllegalArgumentException("Combo must have 4 chars: " + text);
        }
        return new Combo(Card.parse(text.substring(0, 2)), Card.parse(text.substring(2, 4)));
    }

    public String text() {
        return first.text() + second.text();
    }

    public boolean conflictsWith(Card[] board) {
        for (Card card : board) {
            if (first.equals(card) || second.equals(card)) {
                return true;
            }
        }
        return false;
    }

    public boolean conflictsWith(Combo other) {
        return first.equals(other.first) || first.equals(other.second)
                || second.equals(other.first) || second.equals(other.second);
    }

    public Card[] toSeven(Card[] board) {
        Card[] out = new Card[7];
        out[0] = first;
        out[1] = second;
        System.arraycopy(board, 0, out, 2, 5);
        return out;
    }
}
