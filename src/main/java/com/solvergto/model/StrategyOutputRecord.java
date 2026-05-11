package com.solvergto.model;

public record StrategyOutputRecord(
        long runId,
        long jobId,
        String nodeKey,
        int seat,
        String comboCards,
        String actionType,
        double probability,
        Double nodeEv
) {
}
