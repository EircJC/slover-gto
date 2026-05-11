package com.solvergto.web;

import com.solvergto.auth.AuthModels.LoginRequest;
import com.solvergto.auth.AuthModels.ModeRequest;
import com.solvergto.auth.AuthModels.PlayerView;
import com.solvergto.auth.AuthModels.RegisterRequest;
import com.solvergto.auth.AuthSession;
import com.solvergto.service.PlayerService;
import jakarta.servlet.http.HttpSession;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final PlayerService playerService;

    public AuthController(PlayerService playerService) {
        this.playerService = playerService;
    }

    @PostMapping("/register")
    public PlayerView register(@RequestBody RegisterRequest request, HttpSession session) {
        PlayerView player = playerService.register(request.username(), request.password(), request.displayName(), "CASH");
        applySession(session, player);
        return player;
    }

    @PostMapping("/login")
    public PlayerView login(@RequestBody LoginRequest request, HttpSession session) {
        PlayerView player = playerService.login(request.username(), request.password(), "CASH");
        applySession(session, player);
        return player;
    }

    @PostMapping("/mode")
    public PlayerView setMode(@RequestBody ModeRequest request, HttpSession session) {
        long playerId = AuthSession.requirePlayerId(session);
        String mode = playerService.normalizeMode(request.mode());
        session.setAttribute(AuthSession.TRAINER_MODE, mode);
        return playerService.getPlayer(playerId, mode);
    }

    @GetMapping("/me")
    public PlayerView me(HttpSession session) {
        long playerId = AuthSession.requirePlayerId(session);
        return playerService.getPlayer(playerId, AuthSession.mode(session));
    }

    @PostMapping("/logout")
    public void logout(HttpSession session) {
        session.invalidate();
    }

    private void applySession(HttpSession session, PlayerView player) {
        session.setAttribute(AuthSession.PLAYER_ID, player.id());
        session.setAttribute(AuthSession.USERNAME, player.username());
        session.setAttribute(AuthSession.DISPLAY_NAME, player.displayName());
        session.setAttribute(AuthSession.TRAINER_MODE, player.mode());
    }
}
