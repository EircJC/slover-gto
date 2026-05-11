package com.solvergto.service;

import com.solvergto.db.mapper.SolverJobMapper;
import com.solvergto.model.JobRecord;
import org.springframework.stereotype.Service;

@Service
public class SolverJobService {
    private final SolverJobMapper solverJobMapper;

    public SolverJobService(SolverJobMapper solverJobMapper) {
        this.solverJobMapper = solverJobMapper;
    }

    public JobRecord getJob(long jobId) {
        JobRecord job = solverJobMapper.findById(jobId);
        if (job == null) {
            throw new IllegalArgumentException("Job not found: " + jobId);
        }
        return job;
    }

    public void upsertSampleJob() {
        solverJobMapper.upsertSampleJob();
    }
}
