package com.solvergto.web;

import com.solvergto.auth.AuthSession;
import com.solvergto.model.TrainerHandHistoryRecord;
import com.solvergto.model.TrainerOperationRecord;
import com.solvergto.model.TrainerSessionRecord;
import com.solvergto.service.TrainerHandHistoryService;
import com.solvergto.service.TrainerOperationService;
import com.solvergto.service.TrainerSessionService;
import jakarta.servlet.http.HttpSession;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/trainer/history")
public class TrainerHistoryController {
    private final TrainerSessionService trainerSessionService;
    private final TrainerHandHistoryService trainerHandHistoryService;
    private final TrainerOperationService trainerOperationService;

    public TrainerHistoryController(
            TrainerSessionService trainerSessionService,
            TrainerHandHistoryService trainerHandHistoryService,
            TrainerOperationService trainerOperationService
    ) {
        this.trainerSessionService = trainerSessionService;
        this.trainerHandHistoryService = trainerHandHistoryService;
        this.trainerOperationService = trainerOperationService;
    }

    @GetMapping("/sessions")
    public List<TrainerSessionRecord> sessions(@RequestParam(defaultValue = "20") int limit, HttpSession session) {
        return trainerSessionService.listSessions(AuthSession.requirePlayerId(session), AuthSession.mode(session), limit);
    }

    @GetMapping("/session/{sessionId}/hands")
    public List<TrainerHandHistoryRecord> hands(@PathVariable String sessionId, HttpSession session) {
        return trainerHandHistoryService.listBySession(AuthSession.requirePlayerId(session), sessionId);
    }

    @GetMapping("/session/{sessionId}/operations")
    public List<TrainerOperationRecord> operations(@PathVariable String sessionId, HttpSession session) {
        return trainerOperationService.listBySession(AuthSession.requirePlayerId(session), sessionId);
    }
}
