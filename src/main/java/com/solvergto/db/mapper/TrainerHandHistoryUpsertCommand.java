package com.solvergto.db.mapper;

public record TrainerHandHistoryUpsertCommand(
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
