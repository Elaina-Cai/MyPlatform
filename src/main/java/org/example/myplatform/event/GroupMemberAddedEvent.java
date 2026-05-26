package org.example.myplatform.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

/**
 * 群成员加入事件 的事件类
 * 用于通知群成员加入群聊
 */
@Getter
public class GroupMemberAddedEvent extends ApplicationEvent {
    private final Long groupId;
    private final Long userId;
    private final String nickname;
    private final String avatar;

    public GroupMemberAddedEvent(Object source, Long groupId, Long userId, String nickname, String avatar) {
        super(source);
        this.groupId = groupId;
        this.userId = userId;
        this.nickname = nickname;
        this.avatar = avatar;
    }
}