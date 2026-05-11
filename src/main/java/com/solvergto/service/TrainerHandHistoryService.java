package com.solvergto.service;

import com.solvergto.db.mapper.TrainerHandHistoryMapper;
import com.solvergto.db.mapper.TrainerHandHistoryUpsertCommand;
import com.solvergto.model.TrainerHandHistoryRecord;
import com.solvergto.trainer.TrainerModels.HandHistoryView;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class TrainerHandHistoryService {
    private final TrainerHandHistoryMapper trainerHandHistoryMapper;

    public TrainerHandHistoryService(TrainerHandHistoryMapper trainerHandHistoryMapper) {
        this.trainerHandHistoryMapper = trainerHandHistoryMapper;
    }

    public void saveHistory(long playerId, String mode, String sessionId, HandHistoryView history) {
        trainerHandHistoryMapper.upsert(new TrainerHandHistoryUpsertCommand(
                sessionId,
                playerId,
                mode,
                history.handNumber(),
                history.completed(),
                history.heroPosition(),
                history.gtoOpponentPosition(),
                history.heroHand(),
                history.opponentHand(),
                String.join("", history.board()),
                history.scenarioType(),
                history.handScore(),
                history.outcomeLabel(),
                history.summary(),
                history.behaviorAnalysis()
        ));
    }

    public List<TrainerHandHistoryRecord> listBySession(long playerId, String sessionId) {
        return trainerHandHistoryMapper.listBySession(playerId, sessionId);
    }
}
