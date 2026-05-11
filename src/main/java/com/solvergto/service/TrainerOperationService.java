package com.solvergto.service;

import com.solvergto.db.mapper.TrainerOperationMapper;
import com.solvergto.db.mapper.TrainerOperationUpsertCommand;
import com.solvergto.model.TrainerOperationRecord;
import com.solvergto.trainer.TrainerModels.OperationView;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class TrainerOperationService {
    private final TrainerOperationMapper trainerOperationMapper;

    public TrainerOperationService(TrainerOperationMapper trainerOperationMapper) {
        this.trainerOperationMapper = trainerOperationMapper;
    }

    public void saveOperation(long playerId, String mode, String sessionId, int handNumber, OperationView operation) {
        trainerOperationMapper.upsert(new TrainerOperationUpsertCommand(
                sessionId,
                playerId,
                mode,
                handNumber,
                operation.sequence(),
                operation.stage(),
                operation.actor(),
                operation.position(),
                operation.action(),
                operation.detail(),
                operation.automatic(),
                operation.scoreCategory(),
                operation.deltaScore(),
                operation.bestAction(),
                operation.analysis()
        ));
    }

    public List<TrainerOperationRecord> listBySession(long playerId, String sessionId) {
        return trainerOperationMapper.listBySession(playerId, sessionId);
    }
}
