package com.solvergto.api;

import java.util.List;

public record HistorySolveRequest(
        String gameType,
        Integer players,
        String street,
        Double pot,
        Double effectiveStack,
        List<String> board,
        String heroHand,
        String heroPosition,
        String villainPosition,
        List<String> actionHistory,
        List<Double> betSizes,
        Double allinThreshold
) {
}
