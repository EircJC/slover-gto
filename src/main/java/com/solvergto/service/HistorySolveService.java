package com.solvergto.service;

import com.solvergto.api.HistorySolveRequest;
import com.solvergto.api.HistorySolveResponse;
import com.solvergto.model.JobRecord;
import com.solvergto.model.RangeComboRecord;
import com.solvergto.model.TreeNodeRecord;
import com.solvergto.poker.Card;
import com.solvergto.poker.Combo;
import com.solvergto.service.inference.RangeInferenceService;
import com.solvergto.solver.ActionTreeBuilder;
import com.solvergto.solver.RiverSolver;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class HistorySolveService {
    private static final int DEFAULT_ITERATIONS = 1800;
    private final RangeInferenceService rangeInferenceService;

    public HistorySolveService(RangeInferenceService rangeInferenceService) {
        this.rangeInferenceService = rangeInferenceService;
    }

    public HistorySolveResponse solve(HistorySolveRequest request) {
        validate(request);

        RangeInferenceService.InferenceResult inference = rangeInferenceService.infer(request);
        List<RangeComboRecord> ranges = new ArrayList<>();
        ranges.addAll(inference.heroRange());
        ranges.addAll(inference.villainRange());

        List<TreeNodeRecord> nodes = buildTree(request, inference.decisionContext());
        JobRecord job = new JobRecord(
                0L,
                "history-inference-request",
                request.gameType(),
                request.street(),
                boardText(request.board()),
                request.pot(),
                request.effectiveStack(),
                0,
                0,
                DEFAULT_ITERATIONS,
                "ADHOC",
                "Action-history inferred request"
        );

        String heroComboText = Combo.parse(request.heroHand()).text();
        RiverSolver.Analysis analysis = new RiverSolver(job, ranges, nodes).solveAnalysis(heroComboText);
        if ("FAILED".equalsIgnoreCase(analysis.status())) {
            throw new IllegalArgumentException(analysis.message());
        }

        return new HistorySolveResponse(
                analysis.rootActionFrequency(),
                analysis.rootActionEv(),
                analysis.bestAction(),
                analysis.bestEv(),
                analysis.exploitability(),
                inference.decisionContext().decisionMode().name(),
                inference.heroRangeView(),
                inference.villainRangeView(),
                inference.assumptions()
        );
    }

    private List<TreeNodeRecord> buildTree(HistorySolveRequest request, RangeInferenceService.DecisionContext context) {
        return switch (context.decisionMode()) {
            case OPEN -> ActionTreeBuilder.buildOpenDecision(request.effectiveStack(), request.betSizes(), request.allinThreshold());
            case FACING_BET -> ActionTreeBuilder.buildFacingBetDecision(context.callAmount(), request.effectiveStack(),
                    request.betSizes(), request.allinThreshold());
        };
    }

    private void validate(HistorySolveRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Request body is required");
        }
        if (!"NLHE".equalsIgnoreCase(request.gameType())) {
            throw new IllegalArgumentException("Only NLHE is currently supported by the history-based API");
        }
        if (request.players() == null || request.players() != 2) {
            throw new IllegalArgumentException("Only 2-player heads-up spots are currently supported");
        }
        if (!"RIVER".equalsIgnoreCase(request.street())) {
            throw new IllegalArgumentException("Only RIVER is currently supported by the history-based API");
        }
        if (request.pot() == null || request.pot() <= 0.0) {
            throw new IllegalArgumentException("pot must be greater than 0");
        }
        if (request.effectiveStack() == null || request.effectiveStack() <= 0.0) {
            throw new IllegalArgumentException("effectiveStack must be greater than 0");
        }
        if (request.board() == null || request.board().size() != 5) {
            throw new IllegalArgumentException("board must contain exactly 5 cards");
        }
        List<String> normalizedBoard = new ArrayList<>();
        for (String card : request.board()) {
            String normalized = Card.parse(card).text();
            if (normalizedBoard.contains(normalized)) {
                throw new IllegalArgumentException("board contains duplicate card: " + normalized);
            }
            normalizedBoard.add(normalized);
        }
        if (request.heroHand() == null || request.heroHand().isBlank()) {
            throw new IllegalArgumentException("heroHand is required");
        }
        Combo heroHand = Combo.parse(request.heroHand());
        for (String boardCard : request.board()) {
            if (heroHand.conflictsWith(new Card[]{Card.parse(boardCard)})) {
                throw new IllegalArgumentException("heroHand conflicts with board card: " + boardCard);
            }
        }
        if (request.heroPosition() == null || request.heroPosition().isBlank()) {
            throw new IllegalArgumentException("heroPosition is required");
        }
        if (request.villainPosition() == null || request.villainPosition().isBlank()) {
            throw new IllegalArgumentException("villainPosition is required");
        }
        if ((request.betSizes() == null || request.betSizes().isEmpty())
                && (request.allinThreshold() == null || request.allinThreshold() <= 0.0)) {
            throw new IllegalArgumentException("betSizes or allinThreshold must provide at least one action size");
        }
    }

    private String boardText(List<String> board) {
        StringBuilder builder = new StringBuilder();
        for (String card : board) {
            builder.append(Card.parse(card).text());
        }
        return builder.toString();
    }
}
