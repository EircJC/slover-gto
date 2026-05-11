package com.solvergto.solver;

import com.solvergto.model.TreeNodeRecord;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class TreeBuilder {
    private TreeBuilder() {
    }

    public static TreeNode build(List<TreeNodeRecord> records) {
        Map<String, TreeNode> nodes = new HashMap<>();
        TreeNode root = null;
        for (TreeNodeRecord record : records) {
            TreeNode node = new TreeNode(record);
            nodes.put(node.nodeKey(), node);
            if (node.parentNodeKey() == null) {
                if (root != null) {
                    throw new IllegalArgumentException("Multiple root nodes found");
                }
                root = node;
            }
        }
        if (root == null) {
            throw new IllegalArgumentException("Tree has no root node");
        }
        for (TreeNode node : nodes.values()) {
            if (node.parentNodeKey() != null) {
                TreeNode parent = nodes.get(node.parentNodeKey());
                if (parent == null) {
                    throw new IllegalArgumentException("Missing parent for node " + node.nodeKey());
                }
                parent.addChild(node);
            }
        }
        return root;
    }
}
