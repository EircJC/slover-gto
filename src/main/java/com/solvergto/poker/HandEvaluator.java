package com.solvergto.poker;

import java.util.Arrays;

public final class HandEvaluator {
    private HandEvaluator() {
    }

    public static long bestScore(Card[] cards) {
        if (cards.length < 5 || cards.length > 7) {
            throw new IllegalArgumentException("Need 5 to 7 cards");
        }
        if (cards.length == 5) {
            return score5(cards[0], cards[1], cards[2], cards[3], cards[4]);
        }
        long best = Long.MIN_VALUE;
        for (int a = 0; a < cards.length - 4; a++) {
            for (int b = a + 1; b < cards.length - 3; b++) {
                for (int c = b + 1; c < cards.length - 2; c++) {
                    for (int d = c + 1; d < cards.length - 1; d++) {
                        for (int e = d + 1; e < cards.length; e++) {
                            best = Math.max(best, score5(cards[a], cards[b], cards[c], cards[d], cards[e]));
                        }
                    }
                }
            }
        }
        return best;
    }

    public static int compare(Card[] cardsA, Card[] cardsB) {
        return Long.compare(bestScore(cardsA), bestScore(cardsB));
    }

    public static int category(long score) {
        return (int) (score / 759375L);
    }

    private static long score5(Card a, Card b, Card c, Card d, Card e) {
        Card[] cards = {a, b, c, d, e};
        int[] ranks = Arrays.stream(cards).mapToInt(Card::rank).sorted().toArray();
        reverse(ranks);

        boolean flush = allSameSuit(cards);
        int straightHigh = straightHighRank(ranks);
        int[] countByRank = new int[15];
        for (int rank : ranks) {
            countByRank[rank]++;
        }

        int four = findRankWithCount(countByRank, 4);
        int three = findRankWithCount(countByRank, 3);
        int[] pairs = ranksWithCount(countByRank, 2);

        if (flush && straightHigh > 0) {
            return encode(8, straightHigh, 0, 0, 0, 0);
        }
        if (four > 0) {
            int kicker = highestExcluding(ranks, four);
            return encode(7, four, kicker, 0, 0, 0);
        }
        if (three > 0 && pairs.length > 0) {
            return encode(6, three, pairs[0], 0, 0, 0);
        }
        if (flush) {
            return encode(5, ranks[0], ranks[1], ranks[2], ranks[3], ranks[4]);
        }
        if (straightHigh > 0) {
            return encode(4, straightHigh, 0, 0, 0, 0);
        }
        if (three > 0) {
            int[] kickers = highestExcludingTwo(ranks, three);
            return encode(3, three, kickers[0], kickers[1], 0, 0);
        }
        if (pairs.length >= 2) {
            int highPair = Math.max(pairs[0], pairs[1]);
            int lowPair = Math.min(pairs[0], pairs[1]);
            int kicker = highestExcluding(ranks, highPair, lowPair);
            return encode(2, highPair, lowPair, kicker, 0, 0);
        }
        if (pairs.length == 1) {
            int pair = pairs[0];
            int[] kickers = highestExcludingTwo(ranks, pair);
            return encode(1, pair, kickers[0], kickers[1], kickers[2], 0);
        }
        return encode(0, ranks[0], ranks[1], ranks[2], ranks[3], ranks[4]);
    }

    private static boolean allSameSuit(Card[] cards) {
        char suit = Character.toLowerCase(cards[0].suit());
        for (int i = 1; i < cards.length; i++) {
            if (Character.toLowerCase(cards[i].suit()) != suit) {
                return false;
            }
        }
        return true;
    }

    private static int straightHighRank(int[] ranksDesc) {
        int[] ranks = Arrays.stream(ranksDesc).distinct().sorted().toArray();
        if (ranks.length < 5) {
            return 0;
        }
        if (containsWheel(ranks)) {
            return 5;
        }
        int best = 0;
        for (int i = 0; i <= ranks.length - 5; i++) {
            boolean straight = true;
            for (int j = 1; j < 5; j++) {
                if (ranks[i + j] != ranks[i] + j) {
                    straight = false;
                    break;
                }
            }
            if (straight) {
                best = Math.max(best, ranks[i + 4]);
            }
        }
        return best;
    }

    private static boolean containsWheel(int[] ranksAscending) {
        boolean a2 = false, a3 = false, a4 = false, a5 = false, a14 = false;
        for (int rank : ranksAscending) {
            if (rank == 2) a2 = true;
            if (rank == 3) a3 = true;
            if (rank == 4) a4 = true;
            if (rank == 5) a5 = true;
            if (rank == 14) a14 = true;
        }
        return a2 && a3 && a4 && a5 && a14;
    }

    private static int findRankWithCount(int[] counts, int target) {
        for (int rank = 14; rank >= 2; rank--) {
            if (counts[rank] == target) {
                return rank;
            }
        }
        return 0;
    }

    private static int[] ranksWithCount(int[] counts, int target) {
        int[] temp = new int[4];
        int size = 0;
        for (int rank = 14; rank >= 2; rank--) {
            if (counts[rank] == target) {
                temp[size++] = rank;
            }
        }
        return Arrays.copyOf(temp, size);
    }

    private static int highestExcluding(int[] ranksDesc, int... excluded) {
        outer:
        for (int rank : ranksDesc) {
            for (int ex : excluded) {
                if (rank == ex) {
                    continue outer;
                }
            }
            return rank;
        }
        return 0;
    }

    private static int[] highestExcludingTwo(int[] ranksDesc, int excludedRank) {
        int[] out = new int[3];
        int idx = 0;
        for (int rank : ranksDesc) {
            if (rank == excludedRank) {
                continue;
            }
            if (idx < out.length) {
                out[idx++] = rank;
            }
        }
        return out;
    }

    private static long encode(int category, int a, int b, int c, int d, int e) {
        return (((((long) category * 15 + a) * 15 + b) * 15 + c) * 15 + d) * 15 + e;
    }

    private static void reverse(int[] values) {
        for (int i = 0, j = values.length - 1; i < j; i++, j--) {
            int tmp = values[i];
            values[i] = values[j];
            values[j] = tmp;
        }
    }
}
