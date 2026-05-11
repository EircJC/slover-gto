package com.solvergto.db.mapper;

public record TrainerSessionUpsertCommand(
        String sessionId,
        long playerId,
        String mode,
        String status,
        int totalHands,
        int handsPlayed,
        int actionsTaken,
        double rawGtoScore,
        int maxGtoScore,
        int gtoScorePercent,
        String configJson
) {
}
