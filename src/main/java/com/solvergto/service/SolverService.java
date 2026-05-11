package com.solvergto.service;

import com.solvergto.db.SchemaManager;
import com.solvergto.model.JobRecord;
import com.solvergto.model.JobRunRecord;
import com.solvergto.model.RangeComboRecord;
import com.solvergto.model.StrategyOutputRecord;
import com.solvergto.model.TreeNodeRecord;
import com.solvergto.solver.RiverSolver;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.sql.SQLException;
import java.util.List;

@Service
public class SolverService {
    private static final long SAMPLE_JOB_ID = 1L;
    private final SchemaManager schemaManager;
    private final SolverJobService solverJobService;
    private final SolverPlayerRangeService solverPlayerRangeService;
    private final SolverTreeNodeService solverTreeNodeService;
    private final SolverRunService solverRunService;
    private final SolverStrategyService solverStrategyService;

    public SolverService(SchemaManager schemaManager,
                         SolverJobService solverJobService,
                         SolverPlayerRangeService solverPlayerRangeService,
                         SolverTreeNodeService solverTreeNodeService,
                         SolverRunService solverRunService,
                         SolverStrategyService solverStrategyService) {
        this.schemaManager = schemaManager;
        this.solverJobService = solverJobService;
        this.solverPlayerRangeService = solverPlayerRangeService;
        this.solverTreeNodeService = solverTreeNodeService;
        this.solverRunService = solverRunService;
        this.solverStrategyService = solverStrategyService;
    }

    public void initializeSchema() throws SQLException, IOException {
        schemaManager.initialize();
    }

    @Transactional
    public void seedSampleJob() throws SQLException, IOException {
        schemaManager.initialize();
        solverJobService.upsertSampleJob();
        solverPlayerRangeService.deleteByJobId(SAMPLE_JOB_ID);
        solverTreeNodeService.deleteByJobId(SAMPLE_JOB_ID);

        createSampleRanges();
        createSampleTreeNodes();
    }

    @Transactional
    public SolveResponse solveJob(long jobId) throws SQLException {
        JobRecord job = solverJobService.getJob(jobId);
        List<RangeComboRecord> ranges = solverPlayerRangeService.getByJobId(jobId);
        List<TreeNodeRecord> nodes = solverTreeNodeService.getByJobId(jobId);

        RiverSolver solver = new RiverSolver(job, ranges, nodes);
        RiverSolver.Result result = solver.solve();

        JobRunRecord run = solverRunService.create(jobId, "RUNNING");
        List<StrategyOutputRecord> strategies = result.toStrategyOutputs(run.id(), jobId);
        solverStrategyService.saveAll(strategies);
        solverRunService.finish(run.id(), result);

        return new SolveResponse(jobId, run.id(), result.status(), result.iterations(),
                result.oopEv(), result.ipEv(), result.message(), strategies.size());
    }

    private void createSampleRanges() {
        createRange(0, "AhKh", 1.0);
        createRange(0, "QhJh", 1.0);
        createRange(0, "9s9d", 1.0);
        createRange(0, "AcQd", 1.0);
        createRange(1, "AdQh", 1.0);
        createRange(1, "KsQs", 1.0);
        createRange(1, "JhTh", 1.0);
        createRange(1, "8c8s", 1.0);
    }

    private void createSampleTreeNodes() {
        createTreeNode("root", null, 0, "ROOT", 0.0, null, 0);
        createTreeNode("root_check", "root", 1, "CHECK", 0.0, null, 0);
        createTreeNode("root_check_check", "root_check", null, "CHECK", 0.0, "SHOWDOWN", 0);
        createTreeNode("root_check_bet75", "root_check", 0, "BET", 75.0, null, 1);
        createTreeNode("root_check_bet75_fold", "root_check_bet75", null, "FOLD", 0.0, "FOLD", 0);
        createTreeNode("root_check_bet75_call", "root_check_bet75", null, "CALL", 75.0, "SHOWDOWN", 1);
        createTreeNode("root_bet75", "root", 1, "BET", 75.0, null, 1);
        createTreeNode("root_bet75_fold", "root_bet75", null, "FOLD", 0.0, "FOLD", 0);
        createTreeNode("root_bet75_call", "root_bet75", null, "CALL", 75.0, "SHOWDOWN", 1);
        createTreeNode("root_bet75_raise225", "root_bet75", 0, "RAISE", 225.0, null, 2);
        createTreeNode("root_bet75_raise225_fold", "root_bet75_raise225", null, "FOLD", 0.0, "FOLD", 0);
        createTreeNode("root_bet75_raise225_call", "root_bet75_raise225", null, "CALL", 225.0, "SHOWDOWN", 1);
    }

    private void createRange(int seat, String comboCards, double weight) {
        solverPlayerRangeService.create(SAMPLE_JOB_ID, seat, comboCards, weight);
    }

    private void createTreeNode(String nodeKey, String parentNodeKey, Integer actorSeat,
                                String actionType, double amountToAdd, String terminalType, int sortOrder) {
        solverTreeNodeService.create(SAMPLE_JOB_ID, nodeKey, parentNodeKey, actorSeat, actionType, amountToAdd, terminalType, sortOrder);
    }

    public record SolveResponse(
            long jobId,
            long runId,
            String status,
            int iterations,
            double oopEv,
            double ipEv,
            String message,
            int strategyRowCount
    ) {
    }
}
