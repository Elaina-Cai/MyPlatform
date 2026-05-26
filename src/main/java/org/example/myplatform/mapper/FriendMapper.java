package org.example.myplatform.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.example.myplatform.entity.Friend;
import java.util.List;

@Mapper
public interface FriendMapper extends BaseMapper<Friend> {

    @Select("SELECT friend_id FROM friend WHERE user_id = #{userId}")
    List<Long> selectFriendIds(@Param("userId") Long userId);

    @Select("SELECT CASE WHEN from_user_id = #{userId} THEN to_user_id ELSE from_user_id END " +
            "FROM friend WHERE (from_user_id = #{userId} OR to_user_id = #{userId}) AND status = 1 " +
            "GROUP BY CASE WHEN from_user_id = #{userId} THEN to_user_id ELSE from_user_id END")
    List<Long> selectFriendIdsByUserId(@Param("userId") Long userId);

    @Select("SELECT COALESCE(SUM(cnt), 0) FROM (" +
            "SELECT COUNT(*) as cnt FROM friend WHERE from_user_id = #{userId1} AND to_user_id = #{userId2} AND status = 1 " +
            "UNION ALL " +
            "SELECT COUNT(*) FROM friend WHERE from_user_id = #{userId2} AND to_user_id = #{userId1} AND status = 1" +
            ") AS counts")
    int checkFriendship(@Param("userId1") Long userId1, @Param("userId2") Long userId2);
}