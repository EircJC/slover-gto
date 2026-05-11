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
        String opponentHand,
        String boardCards,
        String scenarioType,
        double handScore,
        String outcomeLabel,
        String summary,
        String behaviorAnalysis
) {
}
