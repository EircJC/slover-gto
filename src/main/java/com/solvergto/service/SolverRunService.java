package com.solvergto.service;

import com.solvergto.db.mapper.JobRunCreateCommand;
import com.solvergto.db.mapper.SolverRunMapper;
import com.solvergto.model.JobRunRecord;
import com.solvergto.solver.RiverSolver;
import org.springframework.stereotype.Service;

import java.sql.SQLException;

@Service
public class SolverRunService {
    private final SolverRunMapper solverRunMapper;

    public SolverRunService(SolverRunMapper solverRunMapper) {
        this.solverRunMapper = solverRunMapper;
    }

    public JobRunRecord create(long jobId, String status) throws SQLException {
        JobRunCreateCommand command = new JobRunCreateCommand();
        command.setJobId(jobId);
        command.setStatus(status);
        solverRunMapper.insert(command);
        if (command.getId() == null) {
            throw new SQLException("Failed to create solver_run record");
        }
        return new JobRunRecord(command.getId(), jobId, status, 0, null, null);
    }

    public void finish(long runId, RiverSolver.Result result) {
        solverRunMapper.updateResult(runId, result.status(), result.iterations(), result.oopEv(), result.ipEv(), result.message());
    }
}
