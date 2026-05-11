package com.solvergto.model;

public record PlayerRecord(
        long id,
        String username,
        String passwordHash,
        String displayName,
        String status
) {
}
