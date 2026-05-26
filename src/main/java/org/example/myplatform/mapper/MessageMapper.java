package org.example.myplatform.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.*;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.example.myplatform.entity.Message;
import java.util.List;
import java.util.Map;

@Mapper
public interface MessageMapper extends BaseMapper<Message> {

    // 批量插入消息（写扩散：每条消息存入双方收件箱）
    @Insert("<script>" +
            "INSERT INTO message (sender_id, user_id, friend_id, content, file_url, file_type, created_at, is_read) VALUES " +
            "<foreach collection='messages' item='msg' separator=','>" +
            "(#{msg.senderId}, #{msg.userId}, #{msg.friendId}, #{msg.content}, #{msg.fileUrl}, #{msg.fileType}, #{msg.createdAt}, #{msg.isRead})" +
            "</foreach>" +
            "</script>")
    void insertBatch(@Param("messages") List<Message> messages);

    // 获取用户与好友之间的聊天记录（从自己的收件箱查）
    @Select("SELECT * FROM message " +
            "WHERE user_id = #{userId} AND friend_id = #{friendId} " +
            "ORDER BY created_at ASC")
    List<Message> selectChatHistory(@Param("userId") Long userId, @Param("friendId") Long friendId);

    // 标记消息为已读（在自己的收件箱里标记）
    @Update("UPDATE message SET is_read = 1 " +
            "WHERE user_id = #{userId} AND friend_id = #{friendId} AND is_read = 0 AND sender_id != #{userId}")
    void markAsRead(@Param("userId") Long userId, @Param("friendId") Long friendId);

    // 获取好友未读消息数量映射
    @Select("SELECT sender_id, COUNT(*) as unread_count FROM message " +
            "WHERE user_id = #{userId} AND is_read = 0 AND sender_id != #{userId} " +
            "GROUP BY sender_id")
    List<Map<String, Object>> selectUnreadCountByFriends(@Param("userId") Long userId);

    // 获取用户未读消息数量
    @Select("SELECT COUNT(*) FROM message WHERE user_id = #{userId} AND is_read = 0 AND sender_id != #{userId}")
    long selectUnreadCount(@Param("userId") Long userId);

    /**
     * 删除指定天数之前的消息
     * @param days 天数，如 60 表示删除 60 天前的消息
     */
    @Delete("DELETE FROM message WHERE created_at < DATE_SUB(NOW(), INTERVAL #{days} DAY)")
    void deleteOldMessages(@Param("days") int days);

    @Select("SELECT DISTINCT file_url FROM message WHERE file_url IS NOT NULL AND file_url != ''")
    List<String> selectAllFileUrls();
}