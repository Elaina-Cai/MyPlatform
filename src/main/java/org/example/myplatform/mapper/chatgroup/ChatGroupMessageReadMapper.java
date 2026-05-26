package org.example.myplatform.mapper.chatgroup;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Insert;
import org.example.myplatform.entity.chatgroup.ChatGroupMessageRead;
import java.util.List;

@Mapper
public interface ChatGroupMessageReadMapper extends BaseMapper<ChatGroupMessageRead> {

    @Select("SELECT message_id FROM chat_group_message_read WHERE user_id = #{userId} AND message_id IN " +
            "(SELECT id FROM chat_group_message WHERE group_id = #{groupId})")
    List<Long> selectReadMessageIds(@Param("userId") Long userId, @Param("groupId") Long groupId);

    @Select("SELECT COUNT(*) FROM chat_group_message WHERE group_id = #{groupId} AND sender_id != #{userId} " +
            "AND id NOT IN (SELECT message_id FROM chat_group_message_read WHERE user_id = #{userId})")
    long selectUnreadCount(@Param("groupId") Long groupId, @Param("userId") Long userId);

    @Insert("INSERT INTO chat_group_message_read (message_id, user_id, read_at) VALUES (#{messageId}, #{userId}, NOW())")
    void insertReadRecord(@Param("messageId") Long messageId, @Param("userId") Long userId);

    @Select("SELECT * FROM chat_group_message_read WHERE message_id = #{messageId} AND user_id = #{userId}")
    ChatGroupMessageRead selectByMessageIdAndUserId(@Param("messageId") Long messageId, @Param("userId") Long userId);

    @Delete("DELETE FROM chat_group_message_read WHERE message_id IN (SELECT id FROM chat_group_message WHERE group_id = #{groupId})")
    void deleteByGroupId(@Param("groupId") Long groupId);

    @Insert("<script>" +
            "INSERT INTO chat_group_message_read (message_id, user_id, read_at) " +
            "SELECT id, #{userId}, NOW() FROM chat_group_message " +
            "WHERE group_id = #{groupId} AND sender_id != #{userId} " +
            "AND id NOT IN (SELECT message_id FROM chat_group_message_read WHERE user_id = #{userId})" +
            "</script>")
    void markAllAsRead(@Param("groupId") Long groupId, @Param("userId") Long userId);
}