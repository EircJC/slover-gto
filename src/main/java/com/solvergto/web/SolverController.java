package com.solvergto.web;

import com.solvergto.service.SolverService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.sql.SQLException;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class SolverController {
    private final SolverService solverService;

    public SolverController(SolverService solverService) {
        this.solverService = solverService;
    }

    @PostMapping("/schema/init")
    public Map<String, String> initializeSchema() throws SQLException, IOException {
        solverService.initializeSchema();
        return Map.of("message", "Schema initialized.");
    }

    @PostMapping("/jobs/sample")
    public Map<String, String> seedSampleJob() throws SQLException, IOException {
        solverService.seedSampleJob();
        return Map.of("message", "Sample job seeded as job_id=1.");
    }

    @PostMapping("/jobs/{jobId}/solve")
    public SolverService.SolveResponse solveJob(@PathVariable long jobId) throws SQLException {
        return solverService.solveJob(jobId);
    }

    @ExceptionHandler({IllegalArgumentException.class, SQLException.class, IOException.class})
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, String> handleKnownExceptions(Exception exception) {
        return Map.of("error", exception.getMessage());
    }
}
