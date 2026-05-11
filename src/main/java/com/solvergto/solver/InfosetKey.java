package com.solvergto.solver;

import com.solvergto.poker.Combo;

public record InfosetKey(int seat, String nodeKey, Combo combo) {
    public String text() {
        return seat + "|" + nodeKey + "|" + combo.text();
    }
}
