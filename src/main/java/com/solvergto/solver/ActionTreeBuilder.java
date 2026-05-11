package com.solvergto.solver;

import com.solvergto.model.ActionType;
import com.solvergto.model.TerminalType;
import com.solvergto.model.TreeNodeRecord;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class ActionTreeBuilder {
    private ActionTreeBuilder() {
    }

    public static List<TreeNodeRecord> build(double effectiveStack, List<Double> betSizes, Double allinThreshold) {
        return buildOpenDecision(effectiveStack, betSizes, allinThreshold);
    }

    public static List<TreeNodeRecord> buildOpenDecision(double effectiveStack, List<Double> betSizes, Double allinThreshold) {
        List<Double> actionSizes = normalizeActionSizes(effectiveStack, betSizes, allinThreshold);
        List<TreeNodeRecord> nodes = new ArrayList<>();
        nodes.add(new TreeNodeRecord("root", null, 0, ActionType.ROOT.name(), 0.0, null, 0));
        nodes.add(new TreeNodeRecord("root_check", "root", 1, ActionType.CHECK.name(), 0.0, null, 0));
        nodes.add(new TreeNodeRecord("root_check_check", "root_check", null, ActionType.CHECK.name(), 0.0, TerminalType.SHOWDOWN.name(), 0));

        int sortOrder = 1;
        for (double size : actionSizes) {
            String openKey = "root_" + actionCode(size, effectiveStack, allinThreshold);
            String openAction = size >= allinValue(effectiveStack, allinThreshold) ? ActionType.ALL_IN.name() : ActionType.BET.name();
            nodes.add(new TreeNodeRecord(openKey, "root", 1, openAction, size, null, sortOrder));
            nodes.add(new TreeNodeRecord(openKey + "_fold", openKey, null, ActionType.FOLD.name(), 0.0, TerminalType.FOLD.name(), 0));
            nodes.add(new TreeNodeRecord(openKey + "_call", openKey, null, ActionType.CALL.name(), size, TerminalType.SHOWDOWN.name(), 1));

            String checkBetKey = "root_check_" + actionCode(size, effectiveStack, allinThreshold);
            String checkBetAction = size >= allinValue(effectiveStack, allinThreshold) ? ActionType.ALL_IN.name() : ActionType.BET.name();
            nodes.add(new TreeNodeRecord(checkBetKey, "root_check", 0, checkBetAction, size, null, sortOrder));
            nodes.add(new TreeNodeRecord(checkBetKey + "_fold", checkBetKey, null, ActionType.FOLD.name(), 0.0, TerminalType.FOLD.name(), 0));
            nodes.add(new TreeNodeRecord(checkBetKey + "_call", checkBetKey, null, ActionType.CALL.name(), size, TerminalType.SHOWDOWN.name(), 1));
            sortOrder++;
        }

        return nodes;
    }

    public static List<TreeNodeRecord> buildFacingBetDecision(double callAmount, double effectiveStack,
                                                              List<Double> raiseSizes, Double allinThreshold) {
        if (callAmount <= 0.0) {
            throw new IllegalArgumentException("callAmount must be greater than 0 when facing a bet");
        }
        List<TreeNodeRecord> nodes = new ArrayList<>();
        nodes.add(new TreeNodeRecord("root", null, 0, ActionType.ROOT.name(), 0.0, null, 0));
        nodes.add(new TreeNodeRecord("root_fold", "root", null, ActionType.FOLD.name(), 0.0, TerminalType.FOLD.name(), 0));
        nodes.add(new TreeNodeRecord("root_call", "root", null, ActionType.CALL.name(), callAmount, TerminalType.SHOWDOWN.name(), 1));

        List<Double> actionSizes = normalizeActionSizes(effectiveStack, raiseSizes, allinThreshold);
        int sortOrder = 2;
        for (double raiseTo : actionSizes) {
            if (raiseTo <= callAmount) {
                continue;
            }
            String raiseKey = "root_" + actionCode(raiseTo, effectiveStack, allinThreshold);
            String raiseAction = raiseTo >= allinValue(effectiveStack, allinThreshold) ? ActionType.ALL_IN.name() : ActionType.RAISE.name();
            nodes.add(new TreeNodeRecord(raiseKey, "root", 1, raiseAction, raiseTo, null, sortOrder));
            nodes.add(new TreeNodeRecord(raiseKey + "_fold", raiseKey, null, ActionType.FOLD.name(), 0.0, TerminalType.FOLD.name(), 0));
            nodes.add(new TreeNodeRecord(raiseKey + "_call", raiseKey, null, ActionType.CALL.name(),
                    Math.max(0.0, raiseTo - callAmount), TerminalType.SHOWDOWN.name(), 1));
            sortOrder++;
        }
        return nodes;
    }

    private static List<Double> normalizeActionSizes(double effectiveStack, List<Double> betSizes, Double allinThreshold) {
        Set<Double> sizes = new LinkedHashSet<>();
        if (betSizes != null) {
            for (Double betSize : betSizes) {
                if (betSize == null || betSize <= 0.0) {
                    continue;
                }
                sizes.add(Math.min(betSize, effectiveStack));
            }
        }
        double allin = allinValue(effectiveStack, allinThreshold);
        if (allin > 0.0) {
            sizes.add(allin);
        }
        if (sizes.isEmpty()) {
            throw new IllegalArgumentException("betSizes must contain at least one positive size or allinThreshold must be set");
        }
        return sizes.stream().sorted().toList();
    }

    private static double allinValue(double effectiveStack, Double allinThreshold) {
        if (allinThreshold == null || allinThreshold <= 0.0) {
            return 0.0;
        }
        return Math.min(allinThreshold, effectiveStack);
    }

    private static String actionCode(double size, double effectiveStack, Double allinThreshold) {
        if (size >= allinValue(effectiveStack, allinThreshold)) {
            return "allin";
        }
        if (Math.floor(size) == size) {
            return "bet" + (long) size;
        }
        return "bet" + String.valueOf(size).replace('.', '_');
    }
}
