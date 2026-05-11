package com.solvergto.web;

import com.solvergto.api.HistorySolveRequest;
import com.solvergto.api.HistorySolveResponse;
import com.solvergto.api.PublicSolveRequest;
import com.solvergto.api.PublicSolveResponse;
import com.solvergto.service.HistorySolveService;
import com.solvergto.service.PublicSolveService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/public")
public class PublicSolveController {
    private final HistorySolveService historySolveService;
    private final PublicSolveService publicSolveService;

    public PublicSolveController(HistorySolveService historySolveService, PublicSolveService publicSolveService) {
        this.historySolveService = historySolveService;
        this.publicSolveService = publicSolveService;
    }

    @PostMapping("/solve")
    public PublicSolveResponse solve(@RequestBody PublicSolveRequest request) {
        return publicSolveService.solve(request);
    }

    @PostMapping("/solve-from-history")
    public HistorySolveResponse solveFromHistory(@RequestBody HistorySolveRequest request) {
        return historySolveService.solve(request);
    }
}
