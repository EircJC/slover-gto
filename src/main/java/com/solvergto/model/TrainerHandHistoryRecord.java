package com.solvergto.model;

public record TrainerHandHistoryRecord(
        long id,
        String sessionId,
        long playerId,
        String mode,
        int handNumber,
        boolean completed,
        String heroPosition,
        String gtoOpponentPosition,
        String heroHand,
        String opponentHand,
        String boardCards,
        String scenarioType,
        double handScore,
        String outcomeLabel,
        String summary,
        String behaviorAnalysis
) {
}
