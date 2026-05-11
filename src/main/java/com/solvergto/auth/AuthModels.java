package com.solvergto.auth;

public final class AuthModels {
    private AuthModels() {
    }

    public record RegisterRequest(String username, String password, String displayName) {
    }

    public record LoginRequest(String username, String password) {
    }

    public record ModeRequest(String mode) {
    }

    public record PlayerView(long id, String username, String displayName, String mode) {
    }
}
