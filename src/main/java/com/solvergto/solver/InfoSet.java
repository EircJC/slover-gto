package com.solvergto.solver;

import java.util.Arrays;

final class InfoSet {
    private final int actionCount;
    private final double[] regretSum;
    private final double[] strategySum;

    InfoSet(int actionCount) {
        this.actionCount = actionCount;
        this.regretSum = new double[actionCount];
        this.strategySum = new double[actionCount];
    }

    double[] currentStrategy() {
        double[] strategy = new double[actionCount];
        double normalizer = 0.0;
        for (int i = 0; i < actionCount; i++) {
            strategy[i] = Math.max(regretSum[i], 0.0);
            normalizer += strategy[i];
        }
        if (normalizer > 0.0) {
            for (int i = 0; i < actionCount; i++) {
                strategy[i] /= normalizer;
            }
        } else {
            Arrays.fill(strategy, 1.0 / actionCount);
        }
        return strategy;
    }

    void addRegret(int actionIndex, double value) {
        regretSum[actionIndex] += value;
    }

    void addStrategyWeight(int actionIndex, double value) {
        strategySum[actionIndex] += value;
    }

    double[] averageStrategy() {
        double[] out = new double[actionCount];
        double total = 0.0;
        for (double value : strategySum) {
            total += value;
        }
        if (total <= 0.0) {
            Arrays.fill(out, 1.0 / actionCount);
            return out;
        }
        for (int i = 0; i < actionCount; i++) {
            out[i] = strategySum[i] / total;
        }
        return out;
    }
}
