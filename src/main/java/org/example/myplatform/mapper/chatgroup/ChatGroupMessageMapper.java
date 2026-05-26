package org.example.myplatform.mapper.chatgroup;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.example.myplatform.entity.chatgroup.ChatGroupMessage;
import java.util.List;

@Mapper
public interface ChatGroupMessageMapper extends BaseMapper<ChatGroupMessage> {

    @Select("SELECT * FROM chat_group_message WHERE group_id = #{groupId} ORDER BY created_at ASC LIMIT #{limit} OFFSET #{offset}")
    List<ChatGroupMessage> selectByGroupId(@Param("groupId") Long groupId, @Param("offset") int offset, @Param("limit") int limit);

    @Select("SELECT COUNT(*) FROM chat_group_message WHERE group_id = #{groupId}")
    long countByGroupId(@Param("groupId") Long groupId);

    @Select("SELECT * FROM chat_group_message WHERE group_id = #{groupId} ORDER BY created_at DESC LIMIT 1")
    ChatGroupMessage selectLastMessageByGroupId(@Param("groupId") Long groupId);

    @Delete("DELETE FROM chat_group_message WHERE group_id = #{groupId}")
    void deleteByGroupId(@Param("groupId") Long groupId);

    @Select("SELECT DISTINCT file_url FROM chat_group_message WHERE file_url IS NOT NULL AND file_url != ''")
    List<String> selectAllFileUrls();
}