package com.solvergto.model;

public record TrainerOperationRecord(
        long id,
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
