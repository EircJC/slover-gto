package com.solvergto.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.solvergto.db.mapper.TrainerSessionMapper;
import com.solvergto.db.mapper.TrainerSessionUpsertCommand;
import com.solvergto.model.TrainerSessionRecord;
import com.solvergto.trainer.TrainerModels.SessionView;
import com.solvergto.trainer.TrainerModels.StartRequest;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class TrainerSessionService {
    private final TrainerSessionMapper trainerSessionMapper;
    private final ObjectMapper objectMapper;

    public TrainerSessionService(TrainerSessionMapper trainerSessionMapper, ObjectMapper objectMapper) {
        this.trainerSessionMapper = trainerSessionMapper;
        this.objectMapper = objectMapper;
    }

    public void saveSnapshot(long playerId, String mode, SessionView session, StartRequest config) {
        trainerSessionMapper.upsertSnapshot(toCommand(playerId, mode, session, toJson(config)));
    }

    public void updateSnapshot(long playerId, String mode, SessionView session) {
        trainerSessionMapper.updateSnapshot(toCommand(playerId, mode, session, null));
    }

    public List<TrainerSessionRecord> listSessions(long playerId, String mode, int limit) {
        return trainerSessionMapper.listByPlayerAndMode(playerId, mode, Math.max(1, Math.min(limit, 100)));
    }

    private TrainerSessionUpsertCommand toCommand(long playerId, String mode, SessionView session, String configJson) {
        String status = session.completed() ? "COMPLETED" : session.awaitingNextHand() ? "HAND_DONE" : "RUNNING";
        return new TrainerSessionUpsertCommand(
                session.sessionId(),
                playerId,
                mode,
                status,
                session.totalHands(),
                session.stats().handsPlayed(),
                session.stats().actionsTaken(),
                session.stats().rawGtoScore(),
                session.stats().maxGtoScore(),
                session.stats().gtoScorePercent(),
                configJson
        );
    }

    private String toJson(StartRequest config) {
        try {
            return objectMapper.writeValueAsString(config);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("训练配置序列化失败", exception);
        }
    }
}
