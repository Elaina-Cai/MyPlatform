package org.example.myplatform.mapper.chatgroup;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.example.myplatform.entity.chatgroup.ChatGroupMember;
import java.util.List;
import java.time.LocalDateTime;

@Mapper
public interface ChatGroupMemberMapper extends BaseMapper<ChatGroupMember> {

    @Select("SELECT * FROM chat_group_member WHERE group_id = #{groupId}")
    List<ChatGroupMember> selectByGroupId(@Param("groupId") Long groupId);

    @Select("SELECT * FROM chat_group_member WHERE group_id = #{groupId} AND user_id = #{userId}")
    ChatGroupMember selectByGroupIdAndUserId(@Param("groupId") Long groupId, @Param("userId") Long userId);

    @Select("SELECT COUNT(*) FROM chat_group_member WHERE group_id = #{groupId}")
    int countByGroupId(@Param("groupId") Long groupId);

    @Select("SELECT * FROM chat_group_member WHERE group_id = #{groupId} AND role = 2")
    ChatGroupMember selectOwnerByGroupId(@Param("groupId") Long groupId);

    @Select("SELECT * FROM chat_group_member WHERE group_id = #{groupId} AND role IN (1, 2)")
    List<ChatGroupMember> selectAdminAndOwnerByGroupId(@Param("groupId") Long groupId);

    @Update("UPDATE chat_group_member SET role = #{role} WHERE group_id = #{groupId} AND user_id = #{userId}")
    void updateRole(@Param("groupId") Long groupId, @Param("userId") Long userId, @Param("role") Integer role);

    @Update("UPDATE chat_group_member SET is_muted = #{isMuted}, mute_expire_time = #{muteExpireTime} " +
            "WHERE group_id = #{groupId} AND user_id = #{userId}")
    void updateMuteStatus(@Param("groupId") Long groupId, @Param("userId") Long userId,
                          @Param("isMuted") Integer isMuted, @Param("muteExpireTime") LocalDateTime muteExpireTime);

    @Select("SELECT * FROM chat_group_member WHERE user_id = #{userId}")
    List<ChatGroupMember> selectByUserId(@Param("userId") Long userId);

    @Insert("<script>" +
            "INSERT INTO chat_group_member (group_id, user_id, role, is_muted, joined_at) VALUES " +
            "<foreach collection='list' item='item' separator=','>" +
            "(#{item.groupId}, #{item.userId}, #{item.role}, #{item.isMuted}, #{item.joinedAt})" +
            "</foreach>" +
            "</script>")
    void insertBatch(@Param("list") List<ChatGroupMember> list);

    @Delete("DELETE FROM chat_group_member WHERE group_id = #{groupId}")
    void deleteByGroupId(@Param("groupId") Long groupId);

    @Update("<script>" +
            "UPDATE chat_group_member SET role = #{role} " +
            "WHERE group_id = #{groupId} AND user_id IN " +
            "<foreach collection='userIds' item='userId' open='(' separator=',' close=')'>" +
            "#{userId}" +
            "</foreach>" +
            "</script>")
    void updateRoleBatch(@Param("groupId") Long groupId, @Param("userIds") List<Long> userIds, @Param("role") Integer role);
}