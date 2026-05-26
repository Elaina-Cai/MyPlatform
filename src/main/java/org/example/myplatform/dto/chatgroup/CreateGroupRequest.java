package org.example.myplatform.dto.chatgroup;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;
import java.util.List;

@Data
public class CreateGroupRequest {
    @NotBlank(message = "群名称不能为空")
    private String name;

    private String avatar;
    private String announcement;

    @NotEmpty(message = "成员列表不能为空")
    @Size(min = 3, message = "群聊至少需要3人")
    private List<Long> memberIds;

    private Integer joinType;
    private Integer invitePermission;
    private Integer allowMemberInvite;
}