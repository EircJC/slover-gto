package com.solvergto.service;

import com.solvergto.api.PublicSolveRequest;
import com.solvergto.api.PublicSolveResponse;
import com.solvergto.model.JobRecord;
import com.solvergto.model.RangeComboRecord;
import com.solvergto.model.TreeNodeRecord;
import com.solvergto.poker.Card;
import com.solvergto.poker.RangeNotationParser;
import com.solvergto.solver.ActionTreeBuilder;
import com.solvergto.solver.RiverSolver;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class PublicSolveService {
    private static final int DEFAULT_ITERATIONS = 1500;

    public PublicSolveResponse solve(PublicSolveRequest request) {
        validate(request);

        JobRecord job = new JobRecord(
                0L,
                "adhoc-api-request",
                request.gameType(),
                request.street(),
                boardText(request.board()),
                request.pot(),
                request.effectiveStack(),
                0,
                0,
                DEFAULT_ITERATIONS,
                "ADHOC",
                "In-memory API request"
        );

        List<RangeComboRecord> ranges = new ArrayList<>();
        ranges.addAll(toRangeRecords(0, request.playerRange()));
        ranges.addAll(toRangeRecords(1, request.opponentRange()));

        List<TreeNodeRecord> nodes = ActionTreeBuilder.build(
                request.effectiveStack(),
                request.betSizes(),
                request.allinThreshold()
        );

        RiverSolver.Analysis analysis = new RiverSolver(job, ranges, nodes).solveAnalysis();
        if ("FAILED".equalsIgnoreCase(analysis.status())) {
            throw new IllegalArgumentException(analysis.message());
        }
        return new PublicSolveResponse(
                analysis.rootActionFrequency(),
                analysis.rootActionEv(),
                analysis.bestAction(),
                analysis.bestEv(),
                analysis.exploitability()
        );
    }

    private void validate(PublicSolveRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Request body is required");
        }
        if (!"NLHE".equalsIgnoreCase(request.gameType())) {
            throw new IllegalArgumentException("Only NLHE is currently supported by the public API");
        }
        if (request.players() == null || request.players() != 2) {
            throw new IllegalArgumentException("Only 2-player heads-up spots are currently supported");
        }
        if (!"RIVER".equalsIgnoreCase(request.street())) {
            throw new IllegalArgumentException("Only RIVER is currently supported by the public API");
        }
        if (request.pot() == null || request.pot() <= 0.0) {
            throw new IllegalArgumentException("pot must be greater than 0");
        }
        if (request.effectiveStack() == null || request.effectiveStack() <= 0.0) {
            throw new IllegalArgumentException("effectiveStack must be greater than 0");
        }
        if (request.board() == null || request.board().size() != 5) {
            throw new IllegalArgumentException("board must contain exactly 5 cards for river spots");
        }
        List<String> normalizedBoard = new ArrayList<>();
        for (String card : request.board()) {
            String normalized = Card.parse(card).text();
            if (normalizedBoard.contains(normalized)) {
                throw new IllegalArgumentException("board contains duplicate card: " + normalized);
            }
            normalizedBoard.add(normalized);
        }
        if (request.playerRange() == null || request.playerRange().isBlank()) {
            throw new IllegalArgumentException("playerRange is required");
        }
        if (request.opponentRange() == null || request.opponentRange().isBlank()) {
            throw new IllegalArgumentException("opponentRange is required");
        }
        if ((request.betSizes() == null || request.betSizes().isEmpty())
                && (request.allinThreshold() == null || request.allinThreshold() <= 0.0)) {
            throw new IllegalArgumentException("betSizes or allinThreshold must provide at least one action size");
        }
    }

    private List<RangeComboRecord> toRangeRecords(int seat, String rangeText) {
        List<RangeComboRecord> out = new ArrayList<>();
        for (String comboText : RangeNotationParser.parse(rangeText)) {
            out.add(new RangeComboRecord(seat, comboText, 1.0));
        }
        return out;
    }

    private String boardText(List<String> board) {
        StringBuilder builder = new StringBuilder();
        for (String card : board) {
            builder.append(Card.parse(card).text());
        }
        return builder.toString();
    }
}
