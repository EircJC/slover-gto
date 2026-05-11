package com.solvergto.db.mapper;

import com.solvergto.model.TrainerHandHistoryRecord;
import org.apache.ibatis.annotations.Arg;
import org.apache.ibatis.annotations.ConstructorArgs;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface TrainerHandHistoryMapper {
    @Insert("""
            INSERT INTO trainer_hand_history (
                session_id, player_id, mode, hand_number, completed, hero_position, gto_opponent_position,
                hero_hand, board_cards, scenario_type, hand_score, summary, behavior_analysis
            ) VALUES (
                #{sessionId}, #{playerId}, #{mode}, #{handNumber}, #{completed}, #{heroPosition}, #{gtoOpponentPosition},
                #{heroHand}, #{boardCards}, #{scenarioType}, #{handScore}, #{summary}, #{behaviorAnalysis}
            )
            ON DUPLICATE KEY UPDATE
                completed = VALUES(completed),
                hero_position = VALUES(hero_position),
                gto_opponent_position = VALUES(gto_opponent_position),
                hero_hand = VALUES(hero_hand),
                board_cards = VALUES(board_cards),
                scenario_type = VALUES(scenario_type),
                hand_score = VALUES(hand_score),
                summary = VALUES(summary),
                behavior_analysis = VALUES(behavior_analysis)
            """)
    int upsert(TrainerHandHistoryUpsertCommand command);

    @Select("""
            SELECT id, session_id, player_id, mode, hand_number, completed, hero_position, gto_opponent_position,
                   hero_hand, board_cards, scenario_type, hand_score, summary, behavior_analysis
            FROM trainer_hand_history
            WHERE session_id = #{sessionId}
              AND player_id = #{playerId}
            ORDER BY hand_number
            """)
    @ConstructorArgs({
            @Arg(column = "id", javaType = long.class),
            @Arg(column = "session_id", javaType = String.class),
            @Arg(column = "player_id", javaType = long.class),
            @Arg(column = "mode", javaType = String.class),
            @Arg(column = "hand_number", javaType = int.class),
            @Arg(column = "completed", javaType = boolean.class),
            @Arg(column = "hero_position", javaType = String.class),
            @Arg(column = "gto_opponent_position", javaType = String.class),
            @Arg(column = "hero_hand", javaType = String.class),
            @Arg(column = "board_cards", javaType = String.class),
            @Arg(column = "scenario_type", javaType = String.class),
            @Arg(column = "hand_score", javaType = double.class),
            @Arg(column = "summary", javaType = String.class),
            @Arg(column = "behavior_analysis", javaType = String.class)
    })
    List<TrainerHandHistoryRecord> listBySession(@Param("playerId") long playerId, @Param("sessionId") String sessionId);
}
