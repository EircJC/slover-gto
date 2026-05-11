package com.solvergto.model;

import java.time.LocalDateTime;

public record TrainerSessionRecord(
        long id,
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
        String configJson,
        LocalDateTime startedAt,
        LocalDateTime finishedAt,
        LocalDateTime updatedAt
) {
}
