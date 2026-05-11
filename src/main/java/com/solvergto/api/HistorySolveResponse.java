package com.solvergto.api;

import java.util.List;
import java.util.Map;

public record HistorySolveResponse(
        Map<String, Double> actionFreq,
        Map<String, Double> ev,
        String bestAction,
        double bestEv,
        double expl,
        String rootMode,
        List<WeightedComboView> inferredHeroRange,
        List<WeightedComboView> inferredOpponentRange,
        List<String> assumptions
) {
}
