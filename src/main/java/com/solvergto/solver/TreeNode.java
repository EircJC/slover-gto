package com.solvergto.solver;

import com.solvergto.model.TreeNodeRecord;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class TreeNode {
    private final String nodeKey;
    private final String parentNodeKey;
    private final Integer actorSeat;
    private final String actionType;
    private final double amountToAdd;
    private final String terminalType;
    private final int sortOrder;
    private final List<TreeNode> children = new ArrayList<>();

    public TreeNode(TreeNodeRecord record) {
        this.nodeKey = record.nodeKey();
        this.parentNodeKey = record.parentNodeKey();
        this.actorSeat = record.actorSeat();
        this.actionType = record.actionType();
        this.amountToAdd = record.amountToAdd();
        this.terminalType = record.terminalType();
        this.sortOrder = record.sortOrder();
    }

    public String nodeKey() {
        return nodeKey;
    }

    public String parentNodeKey() {
        return parentNodeKey;
    }

    public Integer actorSeat() {
        return actorSeat;
    }

    public String actionType() {
        return actionType;
    }

    public double amountToAdd() {
        return amountToAdd;
    }

    public String terminalType() {
        return terminalType;
    }

    public int sortOrder() {
        return sortOrder;
    }

    public boolean isTerminal() {
        return terminalType != null;
    }

    public List<TreeNode> children() {
        return Collections.unmodifiableList(children);
    }

    void addChild(TreeNode child) {
        children.add(child);
        children.sort((a, b) -> Integer.compare(a.sortOrder, b.sortOrder));
    }
}
