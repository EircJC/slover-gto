package com.solvergto.service;

import com.solvergto.trainer.TrainerModels.HandHistoryView;
import com.solvergto.trainer.TrainerModels.OperationView;
import com.solvergto.trainer.TrainerModels.SessionView;
import com.solvergto.trainer.TrainerModels.StartRequest;
import org.springframework.stereotype.Service;

@Service
public class TrainerPersistenceService {
    private final TrainerSessionService trainerSessionService;
    private final TrainerHandHistoryService trainerHandHistoryService;
    private final TrainerOperationService trainerOperationService;

    public TrainerPersistenceService(
            TrainerSessionService trainerSessionService,
            TrainerHandHistoryService trainerHandHistoryService,
            TrainerOperationService trainerOperationService
    ) {
        this.trainerSessionService = trainerSessionService;
        this.trainerHandHistoryService = trainerHandHistoryService;
        this.trainerOperationService = trainerOperationService;
    }

    public void saveNewSession(long playerId, String mode, SessionView session, StartRequest config) {
        trainerSessionService.saveSnapshot(playerId, mode, session, config);
        saveHistories(playerId, mode, session);
    }

    public void saveSnapshot(long playerId, String mode, SessionView session) {
        trainerSessionService.updateSnapshot(playerId, mode, session);
        saveHistories(playerId, mode, session);
    }

    private void saveHistories(long playerId, String mode, SessionView session) {
        for (HandHistoryView history : session.handHistories()) {
            trainerHandHistoryService.saveHistory(playerId, mode, session.sessionId(), history);
            for (OperationView operation : history.operations()) {
                trainerOperationService.saveOperation(playerId, mode, session.sessionId(), history.handNumber(), operation);
            }
        }
    }
}
