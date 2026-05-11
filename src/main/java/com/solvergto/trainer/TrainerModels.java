package com.solvergto.trainer;

import java.util.List;
import java.util.Map;

public final class TrainerModels {
    private TrainerModels() {
    }

    public record StartRequest(
            String heroPosition,
            Integer opponentCount,
            List<String> opponentPositions,
            String startStage,
            String scenarioType,
            String gtoOpponentPosition,
            String relativePosition,
            String handRange,
            String handType,
            String drawType,
            Integer sessionHands,
            String blindLevel,
            Integer totalStack
    ) {
    }

    public record ActionRequest(String actionCode) {
    }

    public record StartResponse(String sessionId, SessionView session) {
    }

    public record ActionResponse(SessionView session, FeedbackView feedback) {
    }

    public record SessionView(
            String sessionId,
            boolean completed,
            boolean awaitingNextHand,
            int currentHandNumber,
            int totalHands,
            String trainerLabel,
            ConfigSummaryView configSummary,
            StatsView stats,
            SpotView currentSpot,
            List<HandHistoryView> handHistories
    ) {
    }

    public record ConfigSummaryView(
            String format,
            String heroPosition,
            String opponents,
            String startStage,
            String scenarioType,
            String relativePosition,
            String filters
    ) {
    }

    public record StatsView(
            int handsPlayed,
            int actionsTaken,
            int gtoScore,
            double rawGtoScore,
            int maxGtoScore,
            int gtoScorePercent,
            int bestActionCount,
            int correctActionCount,
            int questionableActionCount,
            int errorActionCount,
            int blunderActionCount
    ) {
    }

    public record SpotView(
            String spotId,
            String stage,
            String scenarioType,
            String decisionMode,
            String heroPosition,
            String focusOpponentPosition,
            List<SeatView> seats,
            List<String> board,
            String heroHand,
            double potSize,
            double effectiveStack,
            List<String> actionHistory,
            String handDescriptor,
            String drawDescriptor,
            List<ActionOptionView> availableActions
    ) {
    }

    public record SeatView(
            String position,
            boolean hero,
            boolean gtoOpponent,
            boolean activeOpponent,
            boolean dealer,
            boolean dimmed,
            String badge,
            double stack,
            double currentBet
    ) {
    }

    public record ActionOptionView(
            String code,
            String label,
            String accent
    ) {
    }

    public record FeedbackView(
            String chosenAction,
            String category,
            int deltaScore,
            String bestAction,
            String summary,
            String explanation,
            Map<String, Double> actionFrequencies,
            Map<String, Double> actionEvs
    ) {
    }

    public record HandHistoryView(
            int handNumber,
            boolean completed,
            String heroPosition,
            String gtoOpponentPosition,
            String heroHand,
            List<String> board,
            String scenarioType,
            double handScore,
            String summary,
            String behaviorAnalysis,
            List<OperationView> operations
    ) {
    }

    public record OperationView(
            int sequence,
            String stage,
            String actor,
            String position,
            String action,
            String detail,
            boolean automatic,
            String scoreCategory,
            int deltaScore,
            String bestAction,
            String analysis
    ) {
    }

    public enum SeatPosition {
        UTG,
        HJ,
        CO,
        BTN,
        SB,
        BB;

        public static SeatPosition parse(String text) {
            return valueOf(text.trim().toUpperCase());
        }
    }

    public enum Stage {
        PREFLOP,
        FLOP,
        TURN,
        RIVER,
        RANDOM;

        public static Stage parse(String text) {
            return valueOf(text.trim().toUpperCase());
        }
    }

    public enum ScenarioType {
        RANDOM,
        OPEN_RAISE,
        FACING_RAISE,
        FACING_3BET,
        FACING_4BET,
        FACING_5BET,
        RAISE_CALL,
        VS_SQUEEZE,
        FACING_LIMP,
        FACING_ISOLATION;

        public static ScenarioType parse(String text) {
            return valueOf(text.trim().toUpperCase());
        }
    }

    public enum RelativePosition {
        RANDOM,
        IP,
        OOP;

        public static RelativePosition parse(String text) {
            return valueOf(text.trim().toUpperCase());
        }
    }

    public enum HandRange {
        RANDOM,
        PREMIUM_HEAVY,
        LINEAR,
        CAPPED,
        BLUFF_HEAVY;

        public static HandRange parse(String text) {
            return valueOf(text.trim().toUpperCase());
        }
    }

    public enum HandType {
        RANDOM,
        PREMIUM,
        POCKET_PAIR,
        SUITED_BROADWAY,
        OFFSUIT_BROADWAY,
        SUITED_CONNECTOR,
        AX,
        BLUFF_CATCHER,
        MONSTER;

        public static HandType parse(String text) {
            return valueOf(text.trim().toUpperCase());
        }
    }

    public enum DrawType {
        RANDOM,
        ANY,
        NONE,
        FLUSH_DRAW,
        STRAIGHT_DRAW,
        COMBO_DRAW,
        BACKDOOR;

        public static DrawType parse(String text) {
            return valueOf(text.trim().toUpperCase());
        }
    }

    public enum DecisionMode {
        OPEN,
        FACING_BET
    }

    public enum ScoreBand {
        BEST(4, "最佳行动"),
        CORRECT(3, "正确行动"),
        QUESTIONABLE(2, "存疑的超低频行动"),
        ERROR(1, "错误行动"),
        BLUNDER(0, "巨大错误");

        private final int points;
        private final String label;

        ScoreBand(int points, String label) {
            this.points = points;
            this.label = label;
        }

        public int points() {
            return points;
        }

        public String label() {
            return label;
        }
    }
}
