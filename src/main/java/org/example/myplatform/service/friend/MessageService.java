package org.example.myplatform.service.friend;

import org.example.myplatform.vo.MessageVO;
import java.util.List;
import java.util.Map;

public interface MessageService {

    void saveMessage(Long senderId, Long receiverId, String content);

    void saveMessage(Long senderId, Long receiverId, String content, String fileUrl, String fileType);

    List<MessageVO> getChatHistory(Long userId, Long friendId);

    void markAsRead(Long userId, Long friendId);

    long getUnreadCount(Long userId);

    Map<Long, Long> getUnreadCountMap(Long userId);
}