package com.solvergto.db.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface SolverRunMapper {
    @Insert("""
            INSERT INTO solver_run (job_id, status, iterations_completed)
            VALUES (#{jobId}, #{status}, 0)
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id", keyColumn = "id")
    int insert(JobRunCreateCommand command);

    @Update("""
            UPDATE solver_run
            SET status = #{status},
                iterations_completed = #{iterationsCompleted},
                oop_ev = #{oopEv},
                ip_ev = #{ipEv},
                message = #{message},
                finished_at = CURRENT_TIMESTAMP
            WHERE id = #{runId}
            """)
    int updateResult(@Param("runId") long runId,
                     @Param("status") String status,
                     @Param("iterationsCompleted") int iterationsCompleted,
                     @Param("oopEv") double oopEv,
                     @Param("ipEv") double ipEv,
                     @Param("message") String message);
}
