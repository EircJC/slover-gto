package com.solvergto.service;

import com.solvergto.db.mapper.SolverStrategyMapper;
import com.solvergto.model.StrategyOutputRecord;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class SolverStrategyService {
    private final SolverStrategyMapper solverStrategyMapper;

    public SolverStrategyService(SolverStrategyMapper solverStrategyMapper) {
        this.solverStrategyMapper = solverStrategyMapper;
    }

    public void saveAll(List<StrategyOutputRecord> rows) {
        if (rows.isEmpty()) {
            return;
        }
        solverStrategyMapper.insertBatch(rows);
    }
}
