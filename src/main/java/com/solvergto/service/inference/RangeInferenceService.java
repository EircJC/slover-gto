package com.solvergto.service.inference;

import com.solvergto.api.HistorySolveRequest;
import com.solvergto.api.WeightedComboView;
import com.solvergto.model.RangeComboRecord;
import com.solvergto.poker.Card;
import com.solvergto.poker.Combo;
import com.solvergto.poker.HandEvaluator;
import com.solvergto.poker.RangeNotationParser;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static com.solvergto.service.inference.ActionHistoryParser.Action;
import static com.solvergto.service.inference.ActionHistoryParser.ActionEvent;
import static com.solvergto.service.inference.ActionHistoryParser.Actor;
import static com.solvergto.service.inference.ActionHistoryParser.SizeKind;
import static com.solvergto.service.inference.ActionHistoryParser.Street;

@Service
public class RangeInferenceService {
    private static final String BTN_TEMPLATE =
            "22+,A2s+,K2s+,Q5s+,J7s+,T7s+,97s+,87s,76s,65s,54s,A2o+,K8o+,Q9o+,J9o+,T9o";
    private static final String BB_TEMPLATE =
            "22+,A2s+,K2s+,Q4s+,J6s+,T6s+,96s+,86s+,76s,65s,54s,A2o+,K7o+,Q8o+,J8o+,T8o+,98o";

    public InferenceResult infer(HistorySolveRequest request) {
        Card heroFirst = Card.parse(request.heroHand().substring(0, 2));
        Card heroSecond = Card.parse(request.heroHand().substring(2, 4));
        Combo heroHand = new Combo(heroFirst, heroSecond);
        Card[] riverBoard = parseBoard(request.board());

        Map<String, Double> heroWeights = new LinkedHashMap<>(initialRange(templateForPosition(request.heroPosition())));
        Map<String, Double> villainWeights = new LinkedHashMap<>(initialRange(templateForPosition(request.villainPosition())));
        heroWeights.putIfAbsent(heroHand.text(), 1.0);

        filterImpossible(heroWeights, riverBoard, null);
        filterImpossible(villainWeights, riverBoard, heroHand);

        List<ActionEvent> events = ActionHistoryParser.parse(request.actionHistory(), request.heroPosition(), request.villainPosition());
        for (ActionEvent event : events) {
            if (event.actor() == Actor.HERO) {
                applyEvent(heroWeights, event, riverBoard);
            } else {
                applyEvent(villainWeights, event, riverBoard);
            }
        }

        ensureRangeNotEmpty(heroWeights, templateForPosition(request.heroPosition()), riverBoard, null, heroHand.text());
        ensureRangeNotEmpty(villainWeights, templateForPosition(request.villainPosition()), riverBoard, heroHand, null);

        DecisionContext decisionContext = decisionContext(events, request.pot(), request.effectiveStack(),
                request.betSizes(), request.allinThreshold(), request.heroPosition(), request.villainPosition());

        return new InferenceResult(
                toRangeRecords(0, heroWeights),
                toRangeRecords(1, villainWeights),
                toWeightedComboViews(heroWeights),
                toWeightedComboViews(villainWeights),
                decisionContext,
                List.of(
                        "当前版本只支持NLHE、2人、River场景。",
                        "系统会按位置加载默认起始范围，并用action history做启发式加权收缩。",
                        "返回的策略频率针对heroHand，但平衡逻辑仍依赖推断出的整条hero范围。"
                )
        );
    }

    private Map<String, Double> initialRange(String template) {
        Map<String, Double> weights = new LinkedHashMap<>();
        for (String comboText : RangeNotationParser.parse(template)) {
            weights.put(comboText, 1.0);
        }
        return weights;
    }

    private void filterImpossible(Map<String, Double> weights, Card[] board, Combo blockedCombo) {
        for (Map.Entry<String, Double> entry : weights.entrySet()) {
            Combo combo = Combo.parse(entry.getKey());
            if (combo.conflictsWith(board) || (blockedCombo != null && combo.conflictsWith(blockedCombo))) {
                entry.setValue(0.0);
            }
        }
    }

    private void applyEvent(Map<String, Double> weights, ActionEvent event, Card[] riverBoard) {
        Card[] stageBoard = stageBoard(riverBoard, event.street());
        double maxWeight = 0.0;
        for (Map.Entry<String, Double> entry : weights.entrySet()) {
            if (entry.getValue() <= 0.0) {
                continue;
            }
            Combo combo = Combo.parse(entry.getKey());
            double next = entry.getValue() * likelihood(combo, event, stageBoard);
            entry.setValue(next);
            maxWeight = Math.max(maxWeight, next);
        }
        pruneTinyWeights(weights, maxWeight);
    }

    private void pruneTinyWeights(Map<String, Double> weights, double maxWeight) {
        if (maxWeight <= 0.0) {
            return;
        }
        double floor = maxWeight * 0.02;
        for (Map.Entry<String, Double> entry : weights.entrySet()) {
            if (entry.getValue() < floor) {
                entry.setValue(0.0);
            }
        }
    }

    private double likelihood(Combo combo, ActionEvent event, Card[] stageBoard) {
        if (event.street() == Street.PREFLOP) {
            return preflopLikelihood(combo, event.action());
        }
        double madeStrength = madeStrength(combo, stageBoard);
        double drawStrength = drawStrength(combo, stageBoard);
        double pressure = pressureScore(combo);
        double middleStrength = 1.0 - Math.min(1.0, Math.abs(madeStrength - 0.55) * 1.7);
        double air = 1.0 - Math.max(madeStrength, drawStrength);
        return switch (event.action()) {
            case BET, RAISE -> clamp(0.08 + 0.80 * madeStrength + 0.38 * drawStrength + 0.12 * pressure - 0.08 * air, 0.03, 1.0);
            case JAM -> clamp(0.06 + 0.88 * madeStrength + 0.26 * drawStrength + 0.08 * pressure - 0.12 * air, 0.02, 1.0);
            case CALL -> clamp(0.10 + 0.52 * madeStrength + 0.42 * drawStrength + 0.18 * middleStrength - 0.05 * air, 0.03, 1.0);
            case CHECK -> clamp(0.22 + 0.35 * (1.0 - madeStrength) + 0.18 * drawStrength + 0.12 * middleStrength, 0.05, 1.0);
            case FOLD -> clamp(0.55 * air + 0.20 * (1.0 - pressure), 0.01, 0.95);
        };
    }

    private double preflopLikelihood(Combo combo, Action action) {
        double strength = preflopStrength(combo);
        double middling = 1.0 - Math.min(1.0, Math.abs(strength - 0.58) * 1.55);
        double trap = combo.first().rank() >= 12 && combo.second().rank() >= 11 ? 0.10 : 0.0;
        return switch (action) {
            case RAISE, BET -> clamp(0.08 + 0.92 * Math.pow(strength, 1.18) + trap, 0.03, 1.0);
            case JAM -> clamp(0.05 + 1.05 * Math.pow(strength, 1.35), 0.02, 1.0);
            case CALL -> clamp(0.12 + 0.55 * middling + 0.22 * playability(combo) + 0.10 * pairBias(combo), 0.04, 1.0);
            case CHECK -> clamp(0.20 + 0.45 * (1.0 - strength) + 0.18 * playability(combo), 0.05, 1.0);
            case FOLD -> clamp(0.60 * (1.0 - strength), 0.01, 0.95);
        };
    }

    private double preflopStrength(Combo combo) {
        int high = Math.max(combo.first().rank(), combo.second().rank());
        int low = Math.min(combo.first().rank(), combo.second().rank());
        if (high == low) {
            return clamp(0.48 + 0.52 * ((high - 2) / 12.0), 0.0, 1.0);
        }
        double strength = 0.14;
        strength += 0.34 * ((high - 2) / 12.0);
        strength += 0.18 * ((low - 2) / 12.0);
        if (combo.first().suit() == combo.second().suit()) {
            strength += 0.10;
        }
        int gap = high - low;
        if (gap == 1) {
            strength += 0.08;
        } else if (gap == 2) {
            strength += 0.04;
        }
        if (high == 14) {
            strength += 0.08;
        }
        if (high >= 11 && low >= 10) {
            strength += 0.08;
        }
        return clamp(strength, 0.0, 1.0);
    }

    private double playability(Combo combo) {
        double value = 0.0;
        if (combo.first().suit() == combo.second().suit()) {
            value += 0.4;
        }
        int gap = Math.abs(combo.first().rank() - combo.second().rank());
        if (gap == 1) {
            value += 0.4;
        } else if (gap == 2) {
            value += 0.2;
        }
        if (Math.max(combo.first().rank(), combo.second().rank()) >= 11) {
            value += 0.2;
        }
        return clamp(value, 0.0, 1.0);
    }

    private double pairBias(Combo combo) {
        return combo.first().rank() == combo.second().rank() ? 1.0 : 0.0;
    }

    private double madeStrength(Combo combo, Card[] board) {
        Card[] cards = merge(combo, board);
        int category = HandEvaluator.category(HandEvaluator.bestScore(cards));
        return switch (category) {
            case 8 -> 1.0;
            case 7 -> 0.98;
            case 6 -> 0.94;
            case 5 -> 0.87;
            case 4 -> 0.82;
            case 3 -> 0.76;
            case 2 -> 0.67;
            case 1 -> 0.46;
            default -> 0.16 + 0.12 * pressureScore(combo);
        };
    }

    private double drawStrength(Combo combo, Card[] board) {
        if (board.length >= 5) {
            return 0.0;
        }
        Card[] cards = merge(combo, board);
        double flush = hasFlushDraw(cards) ? 0.78 : 0.0;
        double straight = hasStraightDraw(cards) ? 0.62 : 0.0;
        return Math.max(flush, straight);
    }

    private boolean hasFlushDraw(Card[] cards) {
        int[] suitCounts = new int[4];
        for (Card card : cards) {
            int index = "shdc".indexOf(Character.toLowerCase(card.suit()));
            if (index >= 0) {
                suitCounts[index]++;
            }
        }
        for (int count : suitCounts) {
            if (count >= 4) {
                return true;
            }
        }
        return false;
    }

    private boolean hasStraightDraw(Card[] cards) {
        boolean[] present = new boolean[15];
        for (Card card : cards) {
            present[card.rank()] = true;
            if (card.rank() == 14) {
                present[1] = true;
            }
        }
        for (int start = 1; start <= 10; start++) {
            int presentCount = 0;
            for (int rank = start; rank < start + 5; rank++) {
                if (present[rank]) {
                    presentCount++;
                }
            }
            if (presentCount >= 4) {
                return true;
            }
        }
        return false;
    }

    private double pressureScore(Combo combo) {
        int premiumCards = 0;
        if (combo.first().rank() >= 11) {
            premiumCards++;
        }
        if (combo.second().rank() >= 11) {
            premiumCards++;
        }
        return premiumCards / 2.0;
    }

    private Card[] merge(Combo combo, Card[] board) {
        Card[] cards = new Card[board.length + 2];
        cards[0] = combo.first();
        cards[1] = combo.second();
        System.arraycopy(board, 0, cards, 2, board.length);
        return cards;
    }

    private Card[] stageBoard(Card[] board, Street street) {
        return switch (street) {
            case PREFLOP -> new Card[0];
            case FLOP -> new Card[]{board[0], board[1], board[2]};
            case TURN -> new Card[]{board[0], board[1], board[2], board[3]};
            case RIVER -> board;
        };
    }

    private void ensureRangeNotEmpty(Map<String, Double> weights, String fallbackTemplate, Card[] board,
                                     Combo blockedCombo, String forcedCombo) {
        boolean hasWeight = weights.values().stream().anyMatch(value -> value > 0.0);
        if (hasWeight) {
            if (forcedCombo != null) {
                weights.putIfAbsent(forcedCombo, 1.0);
            }
            return;
        }
        weights.clear();
        weights.putAll(initialRange(fallbackTemplate));
        filterImpossible(weights, board, blockedCombo);
        if (forcedCombo != null) {
            weights.put(forcedCombo, Math.max(1.0, weights.getOrDefault(forcedCombo, 0.0)));
        }
    }

    private String templateForPosition(String position) {
        String normalized = normalizePosition(position);
        return switch (normalized) {
            case "BTN" -> BTN_TEMPLATE;
            case "BB" -> BB_TEMPLATE;
            default -> throw new IllegalArgumentException("Unsupported position for current inference model: " + position);
        };
    }

    private String normalizePosition(String position) {
        if (position == null || position.isBlank()) {
            throw new IllegalArgumentException("Position is required");
        }
        String normalized = position.trim().toUpperCase(Locale.ROOT);
        if ("SB".equals(normalized)) {
            return "BTN";
        }
        return normalized;
    }

    private DecisionContext decisionContext(List<ActionEvent> events, double pot, double effectiveStack,
                                            List<Double> betSizes, Double allinThreshold,
                                            String heroPosition, String villainPosition) {
        if (events.isEmpty()) {
            return new DecisionContext(DecisionMode.OPEN, 0.0, heroPosition, villainPosition);
        }
        ActionEvent last = events.get(events.size() - 1);
        if (last.actor() == Actor.HERO && last.action() != Action.CHECK) {
            throw new IllegalArgumentException("actionHistory should end before hero's current decision; last aggressive action must be from villain");
        }
        if (last.actor() == Actor.VILLAIN && (last.action() == Action.BET || last.action() == Action.RAISE || last.action() == Action.JAM)) {
            double callAmount = resolveAmount(last, pot, effectiveStack, allinThreshold);
            if (callAmount <= 0.0) {
                callAmount = Math.min(effectiveStack, pot);
            }
            return new DecisionContext(DecisionMode.FACING_BET, callAmount, heroPosition, villainPosition);
        }
        return new DecisionContext(DecisionMode.OPEN, 0.0, heroPosition, villainPosition);
    }

    private double resolveAmount(ActionEvent event, double pot, double effectiveStack, Double allinThreshold) {
        return switch (event.sizeKind()) {
            case CHIPS -> event.sizeValue() == null ? 0.0 : Math.min(event.sizeValue(), effectiveStack);
            case PERCENT_POT -> event.sizeValue() == null ? 0.0 : Math.min(pot * event.sizeValue() / 100.0, effectiveStack);
            case STACK -> Math.min(allinThreshold != null && allinThreshold > 0.0 ? allinThreshold : effectiveStack, effectiveStack);
            case NONE -> 0.0;
        };
    }

    private List<RangeComboRecord> toRangeRecords(int seat, Map<String, Double> weights) {
        double total = weights.values().stream().filter(value -> value > 0.0).mapToDouble(Double::doubleValue).sum();
        if (total <= 0.0) {
            throw new IllegalArgumentException("Inferred range is empty after applying action history");
        }
        List<RangeComboRecord> out = new ArrayList<>();
        for (Map.Entry<String, Double> entry : weights.entrySet()) {
            if (entry.getValue() <= 0.0) {
                continue;
            }
            out.add(new RangeComboRecord(seat, entry.getKey(), entry.getValue() / total));
        }
        return out;
    }

    private List<WeightedComboView> toWeightedComboViews(Map<String, Double> weights) {
        double total = weights.values().stream().filter(value -> value > 0.0).mapToDouble(Double::doubleValue).sum();
        if (total <= 0.0) {
            return List.of();
        }
        return weights.entrySet().stream()
                .filter(entry -> entry.getValue() > 0.0)
                .sorted(Map.Entry.<String, Double>comparingByValue(Comparator.reverseOrder()))
                .map(entry -> new WeightedComboView(entry.getKey(), entry.getValue() / total))
                .toList();
    }

    private Card[] parseBoard(List<String> board) {
        Card[] cards = new Card[board.size()];
        for (int i = 0; i < board.size(); i++) {
            cards[i] = Card.parse(board.get(i));
        }
        return cards;
    }

    private double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    public record InferenceResult(
            List<RangeComboRecord> heroRange,
            List<RangeComboRecord> villainRange,
            List<WeightedComboView> heroRangeView,
            List<WeightedComboView> villainRangeView,
            DecisionContext decisionContext,
            List<String> assumptions
    ) {
    }

    public record DecisionContext(
            DecisionMode decisionMode,
            double callAmount,
            String heroPosition,
            String villainPosition
    ) {
    }

    public enum DecisionMode {
        OPEN,
        FACING_BET
    }
}
