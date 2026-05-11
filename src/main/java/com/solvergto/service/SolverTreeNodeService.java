package com.solvergto.service;

import com.solvergto.db.mapper.SolverTreeNodeMapper;
import com.solvergto.model.TreeNodeRecord;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class SolverTreeNodeService {
    private final SolverTreeNodeMapper solverTreeNodeMapper;

    public SolverTreeNodeService(SolverTreeNodeMapper solverTreeNodeMapper) {
        this.solverTreeNodeMapper = solverTreeNodeMapper;
    }

    public List<TreeNodeRecord> getByJobId(long jobId) {
        return solverTreeNodeMapper.findByJobId(jobId);
    }

    public void deleteByJobId(long jobId) {
        solverTreeNodeMapper.deleteByJobId(jobId);
    }

    public void create(long jobId, String nodeKey, String parentNodeKey, Integer actorSeat,
                       String actionType, double amountToAdd, String terminalType, int sortOrder) {
        solverTreeNodeMapper.insert(jobId, nodeKey, parentNodeKey, actorSeat, actionType, amountToAdd, terminalType, sortOrder);
    }
}
