package com.solvergto.model;

public record TreeNodeRecord(
        String nodeKey,
        String parentNodeKey,
        Integer actorSeat,
        String actionType,
        double amountToAdd,
        String terminalType,
        int sortOrder
) {
}
