package org.example.myplatform.mapper.chatgroup;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.example.myplatform.entity.chatgroup.ChatGroup;

import java.util.List;
import java.util.Map;

@Mapper
public interface ChatGroupMapper extends BaseMapper<ChatGroup> {

    @Select("SELECT * FROM chat_group WHERE id = #{groupId}")
    ChatGroup selectByGroupId(@Param("groupId") Long groupId);

    @Select("SELECT 1 FROM chat_group WHERE id = #{groupId} LIMIT 1")
    Integer existsByGroupId(@Param("groupId") Long groupId);

    @Select("SELECT g.* FROM chat_group g " +
            "INNER JOIN chat_group_member m ON g.id = m.group_id " +
            "WHERE m.user_id = #{userId}")
    List<ChatGroup> selectByUserId(@Param("userId") Long userId);

    @Select("SELECT g.*, COUNT(m.id) as member_count " +
            "FROM chat_group g " +
            "INNER JOIN chat_group_member m ON g.id = m.group_id " +
            "WHERE m.user_id = #{userId} " +
            "GROUP BY g.id")
    List<Map<String, Object>> selectByUserIdWithMemberCount(@Param("userId") Long userId);

    @Select("SELECT * FROM chat_group WHERE id = #{groupId} AND owner_id = #{userId}")
    ChatGroup selectByGroupIdAndOwnerId(@Param("groupId") Long groupId, @Param("userId") Long userId);

    @Delete("DELETE FROM chat_group WHERE id = #{groupId}")
    void deleteByGroupId(@Param("groupId") Long groupId);
}