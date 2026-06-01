package org.example.myplatform.task;

import jakarta.annotation.PostConstruct;
import org.example.myplatform.mapper.MessageMapper;
import org.example.myplatform.mapper.chatgroup.ChatGroupMessageMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class FileCleanupTask {
    
    private static final Logger log = LoggerFactory.getLogger(FileCleanupTask.class);
    
    @Value("${file.upload-path}")
    private String uploadPath;
    
    @Value("${file.cleanup-hours:24}")
    private int cleanupHours;
    
    private final MessageMapper messageMapper;
    private final ChatGroupMessageMapper chatGroupMessageMapper;
    
    public FileCleanupTask(MessageMapper messageMapper, ChatGroupMessageMapper chatGroupMessageMapper) {
        this.messageMapper = messageMapper;
        this.chatGroupMessageMapper = chatGroupMessageMapper;
    }
    
    @PostConstruct
    public void init() {
        log.info("文件清理任务已启动，清理 {} 小时前的未引用文件", cleanupHours);
    }
    
    @Scheduled(cron = "0 0 * * * ?")
    public void cleanupUnusedFiles() {
        log.info("开始清理未引用的文件...");
        
        Path uploadDir = Paths.get(uploadPath);
        if (!Files.exists(uploadDir)) {
            log.warn("上传目录不存在: {}", uploadPath);
            return;
        }
        
        long cutoffTime = System.currentTimeMillis() - (cleanupHours * 60 * 60 * 1000L);
        
        Set<String> referencedFiles = getReferencedFiles();

        AtomicInteger deletedCount = new AtomicInteger(0);
        AtomicInteger skippedCount = new AtomicInteger(0);
        
        try {
            Files.walkFileTree(uploadDir, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    String fileName = file.getFileName().toString();
                    
                    if (!fileName.contains(".")) {
                        return FileVisitResult.CONTINUE;
                    }
                    
                    if (referencedFiles.contains(fileName)) {
                        skippedCount.incrementAndGet();
                        return FileVisitResult.CONTINUE;
                    }
                    
                    try {
                        long fileTime = attrs.lastModifiedTime().toMillis();
                        if (fileTime < cutoffTime) {
                            Files.delete(file);
                            deletedCount.incrementAndGet();
                        } else {
                            skippedCount.incrementAndGet();
                        }
                    } catch (IOException e) {
                        log.warn("删除文件失败: {}", file, e);
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
            
            log.info("文件清理完成，删除 {} 个文件，跳过 {} 个文件", deletedCount.get(), skippedCount.get());
        } catch (IOException e) {
            log.error("文件清理任务执行失败", e);
        }
    }
    
    private Set<String> getReferencedFiles() {
        Set<String> referenced = new HashSet<>();
        
        try {
            var privateFiles = messageMapper.selectAllFileUrls();
            if (privateFiles != null) {
                for (String url : privateFiles) {
                    if (url != null && url.contains("/")) {
                        referenced.add(url.substring(url.lastIndexOf("/") + 1));
                    }
                }
            }
        } catch (Exception e) {
            log.warn("查询私聊文件引用失败", e);
        }
        
        try {
            var groupFiles = chatGroupMessageMapper.selectAllFileUrls();
            if (groupFiles != null) {
                for (String url : groupFiles) {
                    if (url != null && url.contains("/")) {
                        referenced.add(url.substring(url.lastIndexOf("/") + 1));
                    }
                }
            }
        } catch (Exception e) {
            log.warn("查询群聊文件引用失败", e);
        }
        
        return referenced;
    }
}