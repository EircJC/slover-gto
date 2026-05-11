package com.solvergto.db.mapper;

import com.solvergto.model.TrainerSessionRecord;
import org.apache.ibatis.annotations.Arg;
import org.apache.ibatis.annotations.ConstructorArgs;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface TrainerSessionMapper {
    @Insert("""
            INSERT INTO trainer_session (
                session_id, player_id, mode, status, total_hands, hands_played, actions_taken,
                raw_gto_score, max_gto_score, gto_score_percent, config_json
            ) VALUES (
                #{sessionId}, #{playerId}, #{mode}, #{status}, #{totalHands}, #{handsPlayed}, #{actionsTaken},
                #{rawGtoScore}, #{maxGtoScore}, #{gtoScorePercent}, CAST(#{configJson} AS JSON)
            )
            ON DUPLICATE KEY UPDATE
                status = VALUES(status),
                total_hands = VALUES(total_hands),
                hands_played = VALUES(hands_played),
                actions_taken = VALUES(actions_taken),
                raw_gto_score = VALUES(raw_gto_score),
                max_gto_score = VALUES(max_gto_score),
                gto_score_percent = VALUES(gto_score_percent),
                config_json = VALUES(config_json),
                finished_at = IF(VALUES(status) = 'COMPLETED', CURRENT_TIMESTAMP, finished_at)
            """)
    int upsertSnapshot(TrainerSessionUpsertCommand command);

    @Update("""
            UPDATE trainer_session
            SET status = #{status},
                hands_played = #{handsPlayed},
                actions_taken = #{actionsTaken},
                raw_gto_score = #{rawGtoScore},
                max_gto_score = #{maxGtoScore},
                gto_score_percent = #{gtoScorePercent},
                finished_at = IF(#{status} = 'COMPLETED', CURRENT_TIMESTAMP, finished_at)
            WHERE session_id = #{sessionId}
              AND player_id = #{playerId}
            """)
    int updateSnapshot(TrainerSessionUpsertCommand command);

    @Select("""
            SELECT id, session_id, player_id, mode, status, total_hands, hands_played, actions_taken,
                   raw_gto_score, max_gto_score, gto_score_percent, CAST(config_json AS CHAR) AS config_json,
                   started_at, finished_at, updated_at
            FROM trainer_session
            WHERE player_id = #{playerId}
              AND mode = #{mode}
            ORDER BY started_at DESC
            LIMIT #{limit}
            """)
    @ConstructorArgs({
            @Arg(column = "id", javaType = long.class),
            @Arg(column = "session_id", javaType = String.class),
            @Arg(column = "player_id", javaType = long.class),
            @Arg(column = "mode", javaType = String.class),
            @Arg(column = "status", javaType = String.class),
            @Arg(column = "total_hands", javaType = int.class),
            @Arg(column = "hands_played", javaType = int.class),
            @Arg(column = "actions_taken", javaType = int.class),
            @Arg(column = "raw_gto_score", javaType = double.class),
            @Arg(column = "max_gto_score", javaType = int.class),
            @Arg(column = "gto_score_percent", javaType = int.class),
            @Arg(column = "config_json", javaType = String.class),
            @Arg(column = "started_at", javaType = java.time.LocalDateTime.class),
            @Arg(column = "finished_at", javaType = java.time.LocalDateTime.class),
            @Arg(column = "updated_at", javaType = java.time.LocalDateTime.class)
    })
    List<TrainerSessionRecord> listByPlayerAndMode(@Param("playerId") long playerId, @Param("mode") String mode, @Param("limit") int limit);
}
