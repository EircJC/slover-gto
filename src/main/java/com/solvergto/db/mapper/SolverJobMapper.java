package com.solvergto.db.mapper;

import com.solvergto.model.JobRecord;
import org.apache.ibatis.annotations.Arg;
import org.apache.ibatis.annotations.ConstructorArgs;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface SolverJobMapper {
    @Select("""
            SELECT id, job_name, game_variant, street, board_cards, pot_size, effective_stack,
                   hero_seat, first_actor_seat, iterations, status, notes
            FROM solver_job
            WHERE id = #{jobId}
            """)
    @ConstructorArgs({
            @Arg(column = "id", javaType = long.class),
            @Arg(column = "job_name", javaType = String.class),
            @Arg(column = "game_variant", javaType = String.class),
            @Arg(column = "street", javaType = String.class),
            @Arg(column = "board_cards", javaType = String.class),
            @Arg(column = "pot_size", javaType = double.class),
            @Arg(column = "effective_stack", javaType = double.class),
            @Arg(column = "hero_seat", javaType = int.class),
            @Arg(column = "first_actor_seat", javaType = int.class),
            @Arg(column = "iterations", javaType = int.class),
            @Arg(column = "status", javaType = String.class),
            @Arg(column = "notes", javaType = String.class)
    })
    JobRecord findById(@Param("jobId") long jobId);

    @Insert("""
            INSERT INTO solver_job (
                id, job_name, game_variant, street, board_cards, pot_size, effective_stack,
                hero_seat, first_actor_seat, iterations, status, notes
            ) VALUES (
                1, 'sample-river-spot', 'HUNL_RIVER', 'RIVER', 'AsKd7h2cTc',
                150.00, 850.00, 0, 0, 1200, 'PENDING',
                'Sample heads-up river spot'
            )
            ON DUPLICATE KEY UPDATE
                job_name = VALUES(job_name),
                game_variant = VALUES(game_variant),
                street = VALUES(street),
                board_cards = VALUES(board_cards),
                pot_size = VALUES(pot_size),
                effective_stack = VALUES(effective_stack),
                hero_seat = VALUES(hero_seat),
                first_actor_seat = VALUES(first_actor_seat),
                iterations = VALUES(iterations),
                status = VALUES(status),
                notes = VALUES(notes)
            """)
    int upsertSampleJob();
}
