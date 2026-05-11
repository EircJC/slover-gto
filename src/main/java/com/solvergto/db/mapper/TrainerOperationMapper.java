package com.solvergto.db.mapper;

import com.solvergto.model.TrainerOperationRecord;
import org.apache.ibatis.annotations.Arg;
import org.apache.ibatis.annotations.ConstructorArgs;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface TrainerOperationMapper {
    @Insert("""
            INSERT INTO trainer_operation (
                session_id, player_id, mode, hand_number, sequence_no, street, actor, player_position,
                action_text, detail, automatic, score_category, delta_score, best_action, analysis
            ) VALUES (
                #{sessionId}, #{playerId}, #{mode}, #{handNumber}, #{sequenceNo}, #{street}, #{actor}, #{playerPosition},
                #{actionText}, #{detail}, #{automatic}, #{scoreCategory}, #{deltaScore}, #{bestAction}, #{analysis}
            )
            ON DUPLICATE KEY UPDATE
                street = VALUES(street),
                actor = VALUES(actor),
                player_position = VALUES(player_position),
                action_text = VALUES(action_text),
                detail = VALUES(detail),
                automatic = VALUES(automatic),
                score_category = VALUES(score_category),
                delta_score = VALUES(delta_score),
                best_action = VALUES(best_action),
                analysis = VALUES(analysis)
            """)
    int upsert(TrainerOperationUpsertCommand command);

    @Select("""
            SELECT id, session_id, player_id, mode, hand_number, sequence_no, street, actor, player_position,
                   action_text, detail, automatic, score_category, delta_score, best_action, analysis
            FROM trainer_operation
            WHERE session_id = #{sessionId}
              AND player_id = #{playerId}
            ORDER BY hand_number, sequence_no
            """)
    @ConstructorArgs({
            @Arg(column = "id", javaType = long.class),
            @Arg(column = "session_id", javaType = String.class),
            @Arg(column = "player_id", javaType = long.class),
            @Arg(column = "mode", javaType = String.class),
            @Arg(column = "hand_number", javaType = int.class),
            @Arg(column = "sequence_no", javaType = int.class),
            @Arg(column = "street", javaType = String.class),
            @Arg(column = "actor", javaType = String.class),
            @Arg(column = "player_position", javaType = String.class),
            @Arg(column = "action_text", javaType = String.class),
            @Arg(column = "detail", javaType = String.class),
            @Arg(column = "automatic", javaType = boolean.class),
            @Arg(column = "score_category", javaType = String.class),
            @Arg(column = "delta_score", javaType = int.class),
            @Arg(column = "best_action", javaType = String.class),
            @Arg(column = "analysis", javaType = String.class)
    })
    List<TrainerOperationRecord> listBySession(@Param("playerId") long playerId, @Param("sessionId") String sessionId);
}
