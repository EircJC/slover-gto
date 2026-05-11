package com.solvergto.model;

public record JobRunRecord(
        long id,
        long jobId,
        String status,
        int iterationsCompleted,
        Double oopEv,
        Double ipEv
) {
}
