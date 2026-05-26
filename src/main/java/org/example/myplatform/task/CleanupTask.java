package org.example.myplatform.task;

import lombok.RequiredArgsConstructor;
import org.example.myplatform.mapper.MessageMapper;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 定时清理任务
 * <p>功能：定期清理过期的聊天记录</p>
 */
@Component
@RequiredArgsConstructor
public class CleanupTask {

    private final MessageMapper messageMapper;

    /**
     * 每天凌晨 3 点执行
     * 删除 60 天前的聊天记录
     */
    @Scheduled(cron = "0 0 3 * * ?")
    public void cleanupOldMessages() {
        messageMapper.deleteOldMessages(60);
    }
}