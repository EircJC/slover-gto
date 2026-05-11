package com.solvergto.db.mapper;

public record TrainerOperationUpsertCommand(
        String sessionId,
        long playerId,
        String mode,
        int handNumber,
        int sequenceNo,
        String street,
        String actor,
        String playerPosition,
        String actionText,
        String detail,
        boolean automatic,
        String scoreCategory,
        int deltaScore,
        String bestAction,
        String analysis
) {
}
