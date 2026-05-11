package com.solvergto.api;

import java.util.List;

public record PublicSolveRequest(
        String gameType,
        Integer players,
        String street,
        Double pot,
        Double effectiveStack,
        List<String> board,
        String playerRange,
        String opponentRange,
        List<Double> betSizes,
        Double allinThreshold
) {
}
