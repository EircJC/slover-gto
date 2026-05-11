package com.solvergto.db.mapper;

import com.solvergto.model.TreeNodeRecord;
import org.apache.ibatis.annotations.Arg;
import org.apache.ibatis.annotations.ConstructorArgs;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface SolverTreeNodeMapper {
    @Select("""
            SELECT node_key, parent_node_key, actor_seat, action_type, amount_to_add, terminal_type, sort_order
            FROM solver_tree_node
            WHERE job_id = #{jobId}
            ORDER BY sort_order, id
            """)
    @ConstructorArgs({
            @Arg(column = "node_key", javaType = String.class),
            @Arg(column = "parent_node_key", javaType = String.class),
            @Arg(column = "actor_seat", javaType = Integer.class),
            @Arg(column = "action_type", javaType = String.class),
            @Arg(column = "amount_to_add", javaType = double.class),
            @Arg(column = "terminal_type", javaType = String.class),
            @Arg(column = "sort_order", javaType = int.class)
    })
    List<TreeNodeRecord> findByJobId(@Param("jobId") long jobId);

    @Delete("""
            DELETE FROM solver_tree_node
            WHERE job_id = #{jobId}
            """)
    int deleteByJobId(@Param("jobId") long jobId);

    @Insert("""
            INSERT INTO solver_tree_node (
                job_id, node_key, parent_node_key, actor_seat, action_type, amount_to_add, terminal_type, sort_order
            ) VALUES (
                #{jobId}, #{nodeKey}, #{parentNodeKey}, #{actorSeat}, #{actionType}, #{amountToAdd}, #{terminalType}, #{sortOrder}
            )
            """)
    int insert(@Param("jobId") long jobId,
               @Param("nodeKey") String nodeKey,
               @Param("parentNodeKey") String parentNodeKey,
               @Param("actorSeat") Integer actorSeat,
               @Param("actionType") String actionType,
               @Param("amountToAdd") double amountToAdd,
               @Param("terminalType") String terminalType,
               @Param("sortOrder") int sortOrder);
}
