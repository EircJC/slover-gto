package com.solvergto.trainer;

import com.solvergto.auth.AuthSession;
import com.solvergto.trainer.TrainerModels.ActionRequest;
import com.solvergto.trainer.TrainerModels.ActionResponse;
import com.solvergto.trainer.TrainerModels.SessionView;
import com.solvergto.trainer.TrainerModels.StartRequest;
import com.solvergto.trainer.TrainerModels.StartResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/trainer")
public class TrainerController {
    private final TrainerDemoService trainerDemoService;

    public TrainerController(TrainerDemoService trainerDemoService) {
        this.trainerDemoService = trainerDemoService;
    }

    @PostMapping("/start")
    public StartResponse start(@RequestBody StartRequest request, HttpSession session) {
        return trainerDemoService.start(AuthSession.requirePlayerId(session), AuthSession.mode(session), request);
    }

    @PostMapping("/session/{sessionId}/action")
    public ActionResponse act(@PathVariable String sessionId, @RequestBody ActionRequest request, HttpSession session) {
        return trainerDemoService.act(AuthSession.requirePlayerId(session), AuthSession.mode(session), sessionId, request);
    }

    @PostMapping("/session/{sessionId}/next-hand")
    public SessionView nextHand(@PathVariable String sessionId, HttpSession session) {
        return trainerDemoService.nextHand(AuthSession.requirePlayerId(session), AuthSession.mode(session), sessionId);
    }
}
