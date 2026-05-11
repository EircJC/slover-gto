package com.solvergto.auth;

import jakarta.servlet.http.HttpSession;

public final class AuthSession {
    public static final String PLAYER_ID = "playerId";
    public static final String USERNAME = "username";
    public static final String DISPLAY_NAME = "displayName";
    public static final String TRAINER_MODE = "trainerMode";

    private AuthSession() {
    }

    public static long requirePlayerId(HttpSession session) {
        Object playerId = session.getAttribute(PLAYER_ID);
        if (playerId instanceof Number number) {
            return number.longValue();
        }
        throw new IllegalArgumentException("请先登录玩家账号");
    }

    public static String mode(HttpSession session) {
        Object mode = session.getAttribute(TRAINER_MODE);
        return mode == null ? "CASH" : mode.toString();
    }
}
