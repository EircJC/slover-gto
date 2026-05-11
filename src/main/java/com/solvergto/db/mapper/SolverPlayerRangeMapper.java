package com.solvergto.db.mapper;

import com.solvergto.model.RangeComboRecord;
import org.apache.ibatis.annotations.Arg;
import org.apache.ibatis.annotations.ConstructorArgs;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface SolverPlayerRangeMapper {
    @Select("""
            SELECT seat, combo_cards, weight
            FROM solver_player_range
            WHERE job_id = #{jobId}
            ORDER BY seat, id
            """)
    @ConstructorArgs({
            @Arg(column = "seat", javaType = int.class),
            @Arg(column = "combo_cards", javaType = String.class),
            @Arg(column = "weight", javaType = double.class)
    })
    List<RangeComboRecord> findByJobId(@Param("jobId") long jobId);

    @Delete("""
            DELETE FROM solver_player_range
            WHERE job_id = #{jobId}
            """)
    int deleteByJobId(@Param("jobId") long jobId);

    @Insert("""
            INSERT INTO solver_player_range (job_id, seat, combo_cards, weight)
            VALUES (#{jobId}, #{seat}, #{comboCards}, #{weight})
            """)
    int insert(@Param("jobId") long jobId,
               @Param("seat") int seat,
               @Param("comboCards") String comboCards,
               @Param("weight") double weight);
}
