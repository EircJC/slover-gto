package com.solvergto.model;

public record JobRecord(
        long id,
        String jobName,
        String gameVariant,
        String street,
        String boardCards,
        double potSize,
        double effectiveStack,
        int heroSeat,
        int firstActorSeat,
        int iterations,
        String status,
        String notes
) {
}
