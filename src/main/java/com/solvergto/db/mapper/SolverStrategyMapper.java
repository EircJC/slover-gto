package com.solvergto.db.mapper;

import com.solvergto.model.StrategyOutputRecord;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface SolverStrategyMapper {
    @Insert({
            "<script>",
            "INSERT INTO solver_strategy (",
            "  run_id, job_id, node_key, seat, combo_cards, action_type, probability, node_ev",
            ") VALUES ",
            "<foreach collection='rows' item='row' separator=','>",
            "(#{row.runId}, #{row.jobId}, #{row.nodeKey}, #{row.seat}, #{row.comboCards},",
            " #{row.actionType}, #{row.probability}, #{row.nodeEv})",
            "</foreach>",
            "</script>"
    })
    int insertBatch(@Param("rows") List<StrategyOutputRecord> rows);
}
