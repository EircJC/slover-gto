package com.solvergto.db.mapper;

import com.solvergto.model.PlayerRecord;
import org.apache.ibatis.annotations.Arg;
import org.apache.ibatis.annotations.ConstructorArgs;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface PlayerMapper {
    @Select("""
            SELECT id, username, password_hash, display_name, status
            FROM player
            WHERE username = #{username}
            """)
    @ConstructorArgs({
            @Arg(column = "id", javaType = long.class),
            @Arg(column = "username", javaType = String.class),
            @Arg(column = "password_hash", javaType = String.class),
            @Arg(column = "display_name", javaType = String.class),
            @Arg(column = "status", javaType = String.class)
    })
    PlayerRecord findByUsername(@Param("username") String username);

    @Select("""
            SELECT id, username, password_hash, display_name, status
            FROM player
            WHERE id = #{playerId}
            """)
    @ConstructorArgs({
            @Arg(column = "id", javaType = long.class),
            @Arg(column = "username", javaType = String.class),
            @Arg(column = "password_hash", javaType = String.class),
            @Arg(column = "display_name", javaType = String.class),
            @Arg(column = "status", javaType = String.class)
    })
    PlayerRecord findById(@Param("playerId") long playerId);

    @Insert("""
            INSERT INTO player (username, password_hash, display_name, status)
            VALUES (#{username}, #{passwordHash}, #{displayName}, 'ACTIVE')
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(PlayerInsertCommand command);

    @Update("""
            UPDATE player
            SET last_login_at = CURRENT_TIMESTAMP
            WHERE id = #{playerId}
            """)
    int markLogin(@Param("playerId") long playerId);
}
