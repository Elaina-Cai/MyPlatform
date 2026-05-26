package org.example.myplatform.mapper.chatgroup;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.example.myplatform.entity.chatgroup.ChatGroupJoinRequest;
import java.util.List;

@Mapper
public interface ChatGroupJoinRequestMapper extends BaseMapper<ChatGroupJoinRequest> {

    @Select("SELECT * FROM chat_group_join_request WHERE group_id = #{groupId} AND status = 0")
    List<ChatGroupJoinRequest> selectPendingByGroupId(@Param("groupId") Long groupId);

    @Select("SELECT * FROM chat_group_join_request WHERE applicant_id = #{userId} AND status = 0")
    List<ChatGroupJoinRequest> selectPendingByApplicantId(@Param("userId") Long userId);

    @Select("SELECT * FROM chat_group_join_request WHERE group_id = #{groupId} AND applicant_id = #{applicantId} AND status = 0")
    ChatGroupJoinRequest selectPendingRequest(@Param("groupId") Long groupId, @Param("applicantId") Long applicantId);

    @Select("SELECT * FROM chat_group_join_request WHERE applicant_id = #{userId} AND status = 0 AND type = 0")
    List<ChatGroupJoinRequest> selectPendingInvitations(@Param("userId") Long userId);

    @Update("UPDATE chat_group_join_request SET status = #{status}, handled_at = NOW(), handled_by = #{handledBy} WHERE id = #{id}")
    void updateStatus(@Param("id") Long id, @Param("status") Integer status, @Param("handledBy") Long handledBy);

    @Delete("DELETE FROM chat_group_join_request WHERE group_id = #{groupId}")
    void deleteByGroupId(@Param("groupId") Long groupId);
}