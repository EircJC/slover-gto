package com.solvergto.api;

import java.util.Map;

public record PublicSolveResponse(
        Map<String, Double> actionFreq,
        Map<String, Double> ev,
        String bestAction,
        double bestEv,
        double expl
) {
}
