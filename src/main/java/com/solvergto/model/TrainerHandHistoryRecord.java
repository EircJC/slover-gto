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
        String boardCards,
        String scenarioType,
        double handScore,
        String summary,
        String behaviorAnalysis
) {
}
