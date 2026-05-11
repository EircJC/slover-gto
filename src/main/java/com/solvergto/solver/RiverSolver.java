package com.solvergto.solver;

import com.solvergto.model.JobRecord;
import com.solvergto.model.RangeComboRecord;
import com.solvergto.model.StrategyOutputRecord;
import com.solvergto.poker.Card;
import com.solvergto.poker.Combo;
import com.solvergto.poker.HandEvaluator;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class RiverSolver {
    private final JobRecord job;
    private final TreeNode root;
    private final Map<String, TreeNode> treeIndex;
    private final Card[] board;
    private final List<Combo> heroCombos;
    private final List<Combo> villainCombos;
    private final double[] heroWeights;
    private final double[] villainWeights;
    private final boolean[][] validPairs;
    private final double[][] pairWeights;
    private final long[] heroScores;
    private final long[] villainScores;
    private final Map<String, InfoSet> infoSets = new HashMap<>();
    private final double totalPairWeight;

    public RiverSolver(JobRecord job, List<RangeComboRecord> ranges, List<com.solvergto.model.TreeNodeRecord> nodes) {
        this.job = job;
        this.root = TreeBuilder.build(nodes);
        this.treeIndex = indexTree(root);
        this.board = parseBoard(job.boardCards());
        RangeBucket bucket = splitRanges(ranges);
        this.heroCombos = bucket.heroCombos();
        this.villainCombos = bucket.villainCombos();
        this.heroWeights = bucket.heroWeights();
        this.villainWeights = bucket.villainWeights();
        this.validPairs = new boolean[heroCombos.size()][villainCombos.size()];
        this.pairWeights = new double[heroCombos.size()][villainCombos.size()];
        this.heroScores = new long[heroCombos.size()];
        this.villainScores = new long[villainCombos.size()];
        this.totalPairWeight = precomputePairs();
    }

    public Result solve() {
        Analysis analysis = solveAnalysis();
        return new Result(analysis.status(), analysis.iterations(), analysis.oopEv(),
                analysis.ipEv(), analysis.message(), analysis.strategyOutputs());
    }

    public Analysis solveAnalysis() {
        return solveAnalysis(null);
    }

    public Analysis solveAnalysis(String focusHeroComboText) {
        if (heroCombos.isEmpty() || villainCombos.isEmpty()) {
            return Analysis.failed("Ranges are empty");
        }
        if (!"RIVER".equalsIgnoreCase(job.street())) {
            return Analysis.failed("Only river jobs are supported in this MVP");
        }
        if (totalPairWeight <= 0.0) {
            return Analysis.failed("No valid combo pairs after board/blocker filtering");
        }

        int iterations = Math.max(1, job.iterations());
        for (int i = 0; i < iterations; i++) {
            for (int h = 0; h < heroCombos.size(); h++) {
                for (int v = 0; v < villainCombos.size(); v++) {
                    if (!validPairs[h][v]) {
                        continue;
                    }
                    double chanceProb = pairWeights[h][v];
                    traverse(root, h, v, 1.0, 1.0, chanceProb);
                }
            }
        }

        Map<String, double[]> avgStrategy = buildAverageStrategies();
        double oopEv = 0.0;
        double ipEv = 0.0;
        for (int h = 0; h < heroCombos.size(); h++) {
            for (int v = 0; v < villainCombos.size(); v++) {
                if (!validPairs[h][v]) {
                    continue;
                }
                double weight = pairWeights[h][v];
                double utility = evaluate(root, h, v, avgStrategy);
                oopEv += weight * utility;
                ipEv -= weight * utility;
            }
        }

        List<StrategyOutputRecord> outputs = buildStrategyOutputs(avgStrategy);
        RootSummary rootSummary = summarizeRoot(avgStrategy, oopEv, focusHeroComboText);
        return new Analysis("DONE", iterations, oopEv, ipEv, "OK", outputs,
                rootSummary.actionFrequency(), rootSummary.actionEv(),
                rootSummary.bestAction(), rootSummary.bestEv(), rootSummary.exploitability());
    }

    private double traverse(TreeNode node, int heroIndex, int villainIndex,
                            double reachHero, double reachVillain, double chanceProb) {
        if (node.isTerminal()) {
            return terminalUtility(node, heroIndex, villainIndex);
        }
        int actor = node.actorSeat();
        List<TreeNode> children = node.children();
        int actionCount = children.size();
        String infoKey = key(actor, node.nodeKey(), actor == 0 ? heroCombos.get(heroIndex) : villainCombos.get(villainIndex));
        InfoSet infoSet = infoSets.computeIfAbsent(infoKey, k -> new InfoSet(actionCount));
        double[] strategy = infoSet.currentStrategy();
        double[] util = new double[actionCount];
        double nodeUtility = 0.0;
        for (int i = 0; i < actionCount; i++) {
            TreeNode child = children.get(i);
            if (actor == 0) {
                util[i] = traverse(child, heroIndex, villainIndex, reachHero * strategy[i], reachVillain, chanceProb);
            } else {
                util[i] = traverse(child, heroIndex, villainIndex, reachHero, reachVillain * strategy[i], chanceProb);
            }
            nodeUtility += strategy[i] * util[i];
        }
        if (actor == 0) {
            for (int i = 0; i < actionCount; i++) {
                infoSet.addRegret(i, chanceProb * reachVillain * (util[i] - nodeUtility));
                infoSet.addStrategyWeight(i, chanceProb * reachHero * strategy[i]);
            }
        } else {
            for (int i = 0; i < actionCount; i++) {
                infoSet.addRegret(i, chanceProb * reachHero * (nodeUtility - util[i]));
                infoSet.addStrategyWeight(i, chanceProb * reachVillain * strategy[i]);
            }
        }
        return nodeUtility;
    }

    private double evaluate(TreeNode node, int heroIndex, int villainIndex, Map<String, double[]> avgStrategy) {
        if (node.isTerminal()) {
            return terminalUtility(node, heroIndex, villainIndex);
        }
        int actor = node.actorSeat();
        List<TreeNode> children = node.children();
        double[] strategy = avgStrategy.getOrDefault(key(actor, node.nodeKey(),
                actor == 0 ? heroCombos.get(heroIndex) : villainCombos.get(villainIndex)),
                uniform(children.size()));
        double utility = 0.0;
        for (int i = 0; i < children.size(); i++) {
            utility += strategy[i] * evaluate(children.get(i), heroIndex, villainIndex, avgStrategy);
        }
        return utility;
    }

    private Map<String, double[]> buildAverageStrategies() {
        Map<String, double[]> out = new LinkedHashMap<>();
        for (Map.Entry<String, InfoSet> entry : infoSets.entrySet()) {
            out.put(entry.getKey(), entry.getValue().averageStrategy());
        }
        return out;
    }

    private List<StrategyOutputRecord> buildStrategyOutputs(Map<String, double[]> avgStrategy) {
        List<StrategyOutputRecord> out = new ArrayList<>();
        for (Map.Entry<String, InfoSet> entry : infoSets.entrySet()) {
            String key = entry.getKey();
            String[] parts = key.split("\\|", 3);
            int seat = Integer.parseInt(parts[0]);
            String nodeKey = parts[1];
            String comboText = parts[2];
            double[] strategy = avgStrategy.get(key);
            if (strategy == null) {
                continue;
            }
            TreeNode node = findNode(nodeKey);
            List<TreeNode> children = node.children();
            double nodeEv = averageNodeEv(node, seat, comboText, avgStrategy);
            for (int i = 0; i < children.size(); i++) {
                out.add(new StrategyOutputRecord(0L, job.id(), nodeKey, seat, comboText, children.get(i).actionType(), strategy[i], nodeEv));
            }
        }
        return out;
    }

    private double averageNodeEv(TreeNode node, int seat, String comboText, Map<String, double[]> avgStrategy) {
        double total = 0.0;
        double weightSum = 0.0;
        for (int h = 0; h < heroCombos.size(); h++) {
            for (int v = 0; v < villainCombos.size(); v++) {
                if (!validPairs[h][v]) {
                    continue;
                }
                boolean matches = seat == 0 ? heroCombos.get(h).text().equals(comboText) : villainCombos.get(v).text().equals(comboText);
                if (!matches) {
                    continue;
                }
                double weight = pairWeights[h][v];
                total += weight * evaluate(node, h, v, avgStrategy);
                weightSum += weight;
            }
        }
        if (weightSum <= 0.0) {
            return 0.0;
        }
        double heroPerspective = total / weightSum;
        return seat == 0 ? heroPerspective : -heroPerspective;
    }

    private TreeNode findNode(String nodeKey) {
        TreeNode node = treeIndex.get(nodeKey);
        if (node != null) {
            return node;
        }
        throw new IllegalArgumentException("Unknown node: " + nodeKey);
    }

    private double terminalUtility(TreeNode node, int heroIndex, int villainIndex) {
        long heroScore = heroScores[heroIndex];
        long villainScore = villainScores[villainIndex];
        double potStart = job.potSize();

        if ("FOLD".equalsIgnoreCase(node.terminalType())) {
            Integer prevActor = previousActor(node);
            if (prevActor == null) {
                return 0.0;
            }
            int winner = 1 - prevActor;
            return winner == 0 ? potStart / 2.0 + contributionsForSeat(node, 1)
                    : -(potStart / 2.0 + contributionsForSeat(node, 0));
        }

        if (heroScore > villainScore) {
            return potStart / 2.0 + contributionsForSeat(node, 1);
        }
        if (heroScore < villainScore) {
            return -(potStart / 2.0 + contributionsForSeat(node, 0));
        }
        return (contributionsForSeat(node, 1) - contributionsForSeat(node, 0)) / 2.0;
    }

    private Integer previousActor(TreeNode node) {
        if (node.parentNodeKey() == null) {
            return null;
        }
        TreeNode parent = findNode(node.parentNodeKey());
        return parent.actorSeat();
    }

    private double contributionsForSeat(TreeNode terminalNode, int seat) {
        double contribution = 0.0;
        TreeNode current = terminalNode;
        while (current.parentNodeKey() != null) {
            TreeNode parent = findNode(current.parentNodeKey());
            if (parent.actorSeat() != null && parent.actorSeat() == seat) {
                contribution += current.amountToAdd();
            }
            current = parent;
        }
        return contribution;
    }

    private double precomputePairs() {
        for (int h = 0; h < heroCombos.size(); h++) {
            heroScores[h] = HandEvaluator.bestScore(heroCombos.get(h).toSeven(board));
        }
        for (int v = 0; v < villainCombos.size(); v++) {
            villainScores[v] = HandEvaluator.bestScore(villainCombos.get(v).toSeven(board));
        }
        double total = 0.0;
        for (int h = 0; h < heroCombos.size(); h++) {
            for (int v = 0; v < villainCombos.size(); v++) {
                Combo hero = heroCombos.get(h);
                Combo villain = villainCombos.get(v);
                if (hero.conflictsWith(board) || villain.conflictsWith(board) || hero.conflictsWith(villain)) {
                    continue;
                }
                validPairs[h][v] = true;
                double weight = heroWeights[h] * villainWeights[v];
                pairWeights[h][v] = weight;
                total += weight;
            }
        }
        if (total <= 0.0) {
            return 0.0;
        }
        for (int h = 0; h < heroCombos.size(); h++) {
            for (int v = 0; v < villainCombos.size(); v++) {
                pairWeights[h][v] /= total;
            }
        }
        return total;
    }

    private RangeBucket splitRanges(List<RangeComboRecord> ranges) {
        List<Combo> hero = new ArrayList<>();
        List<Combo> villain = new ArrayList<>();
        List<Double> heroWeight = new ArrayList<>();
        List<Double> villainWeight = new ArrayList<>();
        for (RangeComboRecord row : ranges) {
            Combo combo = Combo.parse(row.comboCards());
            if (row.seat() == 0) {
                hero.add(combo);
                heroWeight.add(row.weight());
            } else if (row.seat() == 1) {
                villain.add(combo);
                villainWeight.add(row.weight());
            } else {
                throw new IllegalArgumentException("Unsupported seat: " + row.seat());
            }
        }
        return new RangeBucket(hero, villain, toArray(heroWeight), toArray(villainWeight));
    }

    private Card[] parseBoard(String boardText) {
        if (boardText.length() != 10) {
            throw new IllegalArgumentException("Board must contain 5 cards / 10 chars: " + boardText);
        }
        Card[] cards = new Card[5];
        for (int i = 0; i < 5; i++) {
            cards[i] = Card.parse(boardText.substring(i * 2, i * 2 + 2));
        }
        return cards;
    }

    private String key(int seat, String nodeKey, Combo combo) {
        return seat + "|" + nodeKey + "|" + combo.text();
    }

    private double[] uniform(int size) {
        double[] out = new double[size];
        for (int i = 0; i < size; i++) {
            out[i] = 1.0 / size;
        }
        return out;
    }

    private RootSummary summarizeRoot(Map<String, double[]> avgStrategy, double strategyEv, String focusHeroComboText) {
        if (root.isTerminal() || root.children().isEmpty()) {
            return new RootSummary(Map.of(), Map.of(), null, 0.0, 0.0);
        }
        if (focusHeroComboText != null && !focusHeroComboText.isBlank()) {
            return summarizeRootForHeroCombo(avgStrategy, focusHeroComboText);
        }

        List<TreeNode> children = root.children();
        double[] actionFreq = new double[children.size()];
        double[] actionEv = new double[children.size()];

        for (int h = 0; h < heroCombos.size(); h++) {
            double[] strategy = avgStrategy.getOrDefault(key(root.actorSeat(), root.nodeKey(), heroCombos.get(h)), uniform(children.size()));
            for (int v = 0; v < villainCombos.size(); v++) {
                if (!validPairs[h][v]) {
                    continue;
                }
                double weight = pairWeights[h][v];
                for (int i = 0; i < children.size(); i++) {
                    actionFreq[i] += weight * strategy[i];
                    actionEv[i] += weight * evaluate(children.get(i), h, v, avgStrategy);
                }
            }
        }

        Map<String, Double> frequencyMap = new LinkedHashMap<>();
        Map<String, Double> evMap = new LinkedHashMap<>();
        double bestEv = Double.NEGATIVE_INFINITY;
        String bestAction = null;
        for (int i = 0; i < children.size(); i++) {
            String label = actionLabel(children.get(i));
            frequencyMap.put(label, actionFreq[i]);
            evMap.put(label, actionEv[i]);
            if (actionEv[i] > bestEv) {
                bestEv = actionEv[i];
                bestAction = label;
            }
        }

        return new RootSummary(frequencyMap, evMap, bestAction, bestEv, Math.max(0.0, bestEv - strategyEv));
    }

    private RootSummary summarizeRootForHeroCombo(Map<String, double[]> avgStrategy, String heroComboText) {
        int heroIndex = findHeroComboIndex(heroComboText);
        List<TreeNode> children = root.children();
        double[] strategy = avgStrategy.getOrDefault(key(root.actorSeat(), root.nodeKey(), heroCombos.get(heroIndex)), uniform(children.size()));
        double conditionalWeight = 0.0;
        for (int v = 0; v < villainCombos.size(); v++) {
            if (validPairs[heroIndex][v]) {
                conditionalWeight += pairWeights[heroIndex][v];
            }
        }
        if (conditionalWeight <= 0.0) {
            return new RootSummary(Map.of(), Map.of(), null, 0.0, 0.0);
        }

        double[] actionEv = new double[children.size()];
        double strategyEv = 0.0;
        for (int v = 0; v < villainCombos.size(); v++) {
            if (!validPairs[heroIndex][v]) {
                continue;
            }
            double weight = pairWeights[heroIndex][v] / conditionalWeight;
            strategyEv += weight * evaluate(root, heroIndex, v, avgStrategy);
            for (int i = 0; i < children.size(); i++) {
                actionEv[i] += weight * evaluate(children.get(i), heroIndex, v, avgStrategy);
            }
        }

        Map<String, Double> frequencyMap = new LinkedHashMap<>();
        Map<String, Double> evMap = new LinkedHashMap<>();
        double bestEv = Double.NEGATIVE_INFINITY;
        String bestAction = null;
        for (int i = 0; i < children.size(); i++) {
            String label = actionLabel(children.get(i));
            frequencyMap.put(label, strategy[i]);
            evMap.put(label, actionEv[i]);
            if (actionEv[i] > bestEv) {
                bestEv = actionEv[i];
                bestAction = label;
            }
        }

        return new RootSummary(frequencyMap, evMap, bestAction, bestEv, Math.max(0.0, bestEv - strategyEv));
    }

    private int findHeroComboIndex(String heroComboText) {
        for (int i = 0; i < heroCombos.size(); i++) {
            if (heroCombos.get(i).text().equals(heroComboText)) {
                return i;
            }
        }
        throw new IllegalArgumentException("Hero combo not found in inferred range: " + heroComboText);
    }

    private String actionLabel(TreeNode node) {
        return switch (node.actionType()) {
            case "CHECK" -> "CHECK";
            case "CALL" -> "CALL";
            case "FOLD" -> "FOLD";
            case "ALL_IN" -> "ALLIN";
            case "BET" -> "BET_" + amountLabel(node.amountToAdd());
            case "RAISE" -> "RAISE_" + amountLabel(node.amountToAdd());
            default -> node.actionType();
        };
    }

    private String amountLabel(double amount) {
        if (Math.floor(amount) == amount) {
            return String.valueOf((long) amount);
        }
        return String.format(java.util.Locale.US, "%.2f", amount);
    }

    private double[] toArray(List<Double> values) {
        double[] out = new double[values.size()];
        for (int i = 0; i < values.size(); i++) {
            out[i] = values.get(i);
        }
        normalize(out);
        return out;
    }

    private void normalize(double[] values) {
        double total = 0.0;
        for (double value : values) {
            total += value;
        }
        if (total <= 0.0) {
            if (values.length == 0) {
                return;
            }
            double uniform = 1.0 / values.length;
            for (int i = 0; i < values.length; i++) {
                values[i] = uniform;
            }
            return;
        }
        for (int i = 0; i < values.length; i++) {
            values[i] /= total;
        }
    }

    private Map<String, TreeNode> indexTree(TreeNode root) {
        Map<String, TreeNode> out = new HashMap<>();
        walk(root, out);
        return out;
    }

    private void walk(TreeNode node, Map<String, TreeNode> out) {
        out.put(node.nodeKey(), node);
        for (TreeNode child : node.children()) {
            walk(child, out);
        }
    }

    private record RangeBucket(List<Combo> heroCombos, List<Combo> villainCombos, double[] heroWeights, double[] villainWeights) {
    }

    private record RootSummary(Map<String, Double> actionFrequency, Map<String, Double> actionEv,
                               String bestAction, double bestEv, double exploitability) {
    }

    public record Result(String status, int iterations, double oopEv, double ipEv, String message,
                         List<StrategyOutputRecord> strategyOutputs) {
        public Result(String status, int iterations, double oopEv, double ipEv, String message) {
            this(status, iterations, oopEv, ipEv, message, List.of());
        }

        public List<StrategyOutputRecord> toStrategyOutputs(long runId, long jobId) {
            List<StrategyOutputRecord> out = new ArrayList<>(strategyOutputs.size());
            for (StrategyOutputRecord row : strategyOutputs) {
                out.add(new StrategyOutputRecord(runId, jobId, row.nodeKey(), row.seat(), row.comboCards(), row.actionType(), row.probability(), row.nodeEv()));
            }
            return out;
        }
    }

    public record Analysis(String status, int iterations, double oopEv, double ipEv, String message,
                           List<StrategyOutputRecord> strategyOutputs,
                           Map<String, Double> rootActionFrequency,
                           Map<String, Double> rootActionEv,
                           String bestAction,
                           double bestEv,
                           double exploitability) {
        static Analysis failed(String message) {
            return new Analysis("FAILED", 0, 0.0, 0.0, message, List.of(),
                    Map.of(), Map.of(), null, 0.0, 0.0);
        }
    }
}
