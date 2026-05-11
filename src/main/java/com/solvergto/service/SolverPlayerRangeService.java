package com.solvergto.service;

import com.solvergto.db.mapper.SolverPlayerRangeMapper;
import com.solvergto.model.RangeComboRecord;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class SolverPlayerRangeService {
    private final SolverPlayerRangeMapper solverPlayerRangeMapper;

    public SolverPlayerRangeService(SolverPlayerRangeMapper solverPlayerRangeMapper) {
        this.solverPlayerRangeMapper = solverPlayerRangeMapper;
    }

    public List<RangeComboRecord> getByJobId(long jobId) {
        return solverPlayerRangeMapper.findByJobId(jobId);
    }

    public void deleteByJobId(long jobId) {
        solverPlayerRangeMapper.deleteByJobId(jobId);
    }

    public void create(long jobId, int seat, String comboCards, double weight) {
        solverPlayerRangeMapper.insert(jobId, seat, comboCards, weight);
    }
}
