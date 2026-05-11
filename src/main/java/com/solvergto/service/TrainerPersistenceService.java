package com.solvergto.service;

import com.solvergto.db.SchemaManager;
import com.solvergto.trainer.TrainerModels.HandHistoryView;
import com.solvergto.trainer.TrainerModels.OperationView;
import com.solvergto.trainer.TrainerModels.SessionView;
import com.solvergto.trainer.TrainerModels.StartRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class TrainerPersistenceService {
    private static final Logger log = LoggerFactory.getLogger(TrainerPersistenceService.class);

    private final SchemaManager schemaManager;
    private final TrainerSessionService trainerSessionService;
    private final TrainerHandHistoryService trainerHandHistoryService;
    private final TrainerOperationService trainerOperationService;

    public TrainerPersistenceService(
            SchemaManager schemaManager,
            TrainerSessionService trainerSessionService,
            TrainerHandHistoryService trainerHandHistoryService,
            TrainerOperationService trainerOperationService
    ) {
        this.schemaManager = schemaManager;
        this.trainerSessionService = trainerSessionService;
        this.trainerHandHistoryService = trainerHandHistoryService;
        this.trainerOperationService = trainerOperationService;
    }

    public void saveNewSession(long playerId, String mode, SessionView session, StartRequest config) {
        ensureSchemaReady();
        trainerSessionService.saveSnapshot(playerId, mode, session, config);
        saveHistories(playerId, mode, session);
    }

    public void saveSnapshot(long playerId, String mode, SessionView session) {
        ensureSchemaReady();
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

    private void ensureSchemaReady() {
        try {
            schemaManager.ensureTrainerSchema();
        } catch (Exception exception) {
            String message = rootCauseMessage(exception);
            log.error("Trainer schema initialization failed: {}", message, exception);
            throw new IllegalStateException("训练数据表初始化失败，请检查数据库权限和表结构。详细原因：" + message, exception);
        }
    }

    private String rootCauseMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        return current.getMessage() == null || current.getMessage().isBlank()
                ? current.getClass().getSimpleName()
                : current.getMessage();
    }
}
