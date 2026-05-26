package org.example.myplatform.controller;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.myplatform.interceptor.JwtInterceptor;
import org.example.myplatform.service.friend.MessageService;
import org.example.myplatform.vo.MessageVO;
import org.example.myplatform.vo.Result;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/message")
@RequiredArgsConstructor
public class MessageController {

    private final MessageService messageService;

    /**
     * 获取聊天记录
     */
    @GetMapping("/history")
    public Result<List<MessageVO>> getHistory(
            @RequestParam Long friendId,
            HttpServletRequest request
    ) {
        Long currentUserId = (Long) request.getAttribute(JwtInterceptor.USER_ID_ATTRIBUTE);
        log.info("获取聊天记录: userId={}, friendId={}", currentUserId, friendId);
        List<MessageVO> history = messageService.getChatHistory(currentUserId, friendId);
        log.debug("查询到消息数: {}", history.size());
        messageService.markAsRead(currentUserId, friendId);
        return Result.success(history);
    }

    /**
     * 获取未读消息数
     */
    @GetMapping("/unread")
    public Result<Map<String, Object>> getUnreadCount(HttpServletRequest request) {
        Long currentUserId = (Long) request.getAttribute(JwtInterceptor.USER_ID_ATTRIBUTE);
        long count = messageService.getUnreadCount(currentUserId);
        return Result.success(Map.of("unreadCount", count));
    }
}