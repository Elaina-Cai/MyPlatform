package org.example.myplatform.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.util.unit.DataSize;
import org.example.myplatform.exception.BadRequestException;
import org.example.myplatform.exception.UnauthorizedException;
import org.example.myplatform.interceptor.JwtInterceptor;
import org.example.myplatform.vo.Result;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 文件上传控制器
 * 用于处理文件上传相关的请求
 */
@RestController
@RequestMapping("/api/upload")
public class FileController {

    @Value("${file.upload-path}")
    private String uploadPath;

    @Value("${file.max-size}")
    private DataSize maxSize;

    private static final Set<String> IMAGE_TYPES = Set.of(
            "image/jpeg", "image/png", "image/gif", "image/webp",
            "image/heic", "image/heif", "image/bmp", "image/tiff", "image/x-icon"
    );
    private static final Set<String> VIDEO_TYPES = Set.of(
            "video/mp4", "video/quicktime", "video/x-msvideo", "video/webm",
            "video/x-matroska", "video/x-ms-wmv", "video/mpeg"
    );

    private static final Set<String> ALLOWED_IMAGE_EXTENSIONS = Set.of(
            ".jpg", ".jpeg", ".png", ".gif", ".webp", ".heic", ".heif", ".bmp", ".tiff", ".ico"
    );
    private static final Set<String> ALLOWED_VIDEO_EXTENSIONS = Set.of(
            ".mp4", ".mov", ".avi", ".webm", ".mkv", ".wmv", ".mpeg"
    );

    /**
     * 获取上传配置（公开接口）
     */
    @GetMapping("/config")
    public Result<Map<String, Object>> getUploadConfig() {
        Map<String, Object> config = new HashMap<>();
        config.put("maxSize", maxSize.toBytes());
        return Result.success(config);
    }

    /**
     * 上传文件
     * @param file 上传的文件
     * @return 上传结果
     */
    @PostMapping("/file")
    public Result<Map<String, String>> uploadFile(
            @RequestParam("file") MultipartFile file,
            HttpServletRequest request) {

        Long userId = (Long) request.getAttribute(JwtInterceptor.USER_ID_ATTRIBUTE);
        if (userId == null) {
            throw new UnauthorizedException("未登录");
        }

        if (file.isEmpty()) {
            throw new BadRequestException("文件不能为空");
        }

        if (file.getSize() > maxSize.toBytes()) {
            throw new BadRequestException("文件大小不能超过100MB");
        }

        String contentType = file.getContentType();
        if (contentType == null || (!IMAGE_TYPES.contains(contentType) && !VIDEO_TYPES.contains(contentType))) {
            throw new BadRequestException("仅支持图片和视频文件");
        }

        String originalFilename = file.getOriginalFilename();
        String ext = "";
        if (originalFilename != null && originalFilename.contains(".")) {
            ext = originalFilename.substring(originalFilename.lastIndexOf(".")).toLowerCase();
        }

        if (IMAGE_TYPES.contains(contentType)) {
            if (!ALLOWED_IMAGE_EXTENSIONS.contains(ext)) {
                throw new BadRequestException("不支持的图片文件扩展名");
            }
        } else {
            if (!ALLOWED_VIDEO_EXTENSIONS.contains(ext)) {
                throw new BadRequestException("不支持的视频文件扩展名");
            }
        }

        if (!isValidFileContent(file, contentType)) {
            throw new BadRequestException("文件内容无效或文件被篡改");
        }
        String newFilename = UUID.randomUUID().toString() + ext;

        try {
            Path uploadDir = Paths.get(uploadPath);
            if (!Files.exists(uploadDir)) {
                Files.createDirectories(uploadDir);
            }

            Path filePath = uploadDir.resolve(newFilename);
            file.transferTo(filePath.toFile());

            String fileUrl = "/uploads/files/" + newFilename;

            Map<String, String> result = new HashMap<>();
            result.put("fileUrl", fileUrl);
            result.put("fileType", IMAGE_TYPES.contains(contentType) ? "image" : "video");
            result.put("fileName", originalFilename);

            return Result.success(result);
        } catch (IOException e) {
            throw new BadRequestException("文件上传失败: " + e.getMessage());
        }
    }

    private boolean isValidFileContent(MultipartFile file, String contentType) {
        try {
            byte[] header = Arrays.copyOf(file.getBytes(), 16);

            if ("image/jpeg".equals(contentType)) {
                return header[0] == (byte)0xFF && header[1] == (byte)0xD8 && header[2] == (byte)0xFF;
            }
            if ("image/png".equals(contentType)) {
                return header[0] == (byte)0x89 && header[1] == 0x50 && header[2] == 0x4E && header[3] == 0x47;
            }
            if ("image/gif".equals(contentType)) {
                return header[0] == 0x47 && header[1] == 0x49 && header[2] == 0x46;
            }
            if ("image/bmp".equals(contentType)) {
                return header[0] == 0x42 && header[1] == 0x4D;
            }
            if ("image/webp".equals(contentType)) {
                return header[0] == 0x52 && header[1] == 0x49 && header[2] == 0x46 && header[3] == 0x46;
            }
            if ("video/mp4".equals(contentType)) {
                return (header[4] == 0x66 && header[5] == 0x74 && header[6] == 0x79 && header[7] == 0x70) ||
                       (header[0] == 0x00 && header[1] == 0x00 && header[2] == 0x00);
            }
            if ("video/quicktime".equals(contentType)) {
                return header[0] == 0x00 && header[1] == 0x00 && header[2] == 0x00;
            }
            if ("video/x-msvideo".equals(contentType)) {
                return (header[0] == 0x00 && header[1] == 0x00 && header[2] == 0x01) ||
                       (header[0] == 0x00 && header[1] == 0x00 && header[2] == 0x02);
            }
            if ("video/webm".equals(contentType)) {
                return header[0] == 0x1A && header[1] == 0x45 && header[2] == (byte)0xDF && header[3] == (byte)0xA3;
            }

            return true;
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * 删除文件
     * @param fileUrl 文件的URL路径
     * @return 删除结果
     */
    @DeleteMapping("/file")
    public Result<Void> deleteFile(
            @RequestParam("fileUrl") String fileUrl,
            HttpServletRequest request) {

        Long userId = (Long) request.getAttribute(JwtInterceptor.USER_ID_ATTRIBUTE);
        if (userId == null) {
            throw new UnauthorizedException("未登录");
        }

        if (fileUrl == null || !fileUrl.startsWith("/uploads/files/")) {
            throw new BadRequestException("无效的文件路径");
        }

        try {
            Path filePath = Paths.get(uploadPath).resolve(fileUrl.substring("/uploads/files/".length()));
            if (Files.exists(filePath)) {
                Files.delete(filePath);
            }
            return Result.success(null);
        } catch (IOException e) {
            throw new BadRequestException("删除文件失败");
        }
    }
}