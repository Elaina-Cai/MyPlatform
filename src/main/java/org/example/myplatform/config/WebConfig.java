package org.example.myplatform.config;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.myplatform.interceptor.JwtInterceptor;
import org.example.myplatform.interceptor.RateLimitInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Path;
import java.nio.file.Paths;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class WebConfig implements WebMvcConfigurer {

    private final JwtInterceptor jwtInterceptor;
    private final RateLimitInterceptor rateLimitInterceptor;

    @Value("${avatar.upload-path}")
    private String avatarUploadPath;

    @Value("${file.upload-path}")
    private String fileUploadPath;

    @Value("${cors.allowed-origins:*}")
    private String allowedOrigins;
    /**
     * 配置跨域规则
     *
     * 说明：
     * - 浏览器出于安全考虑，禁止页面向不同源（端口/域名）的服务器发请求
     * - 当前端运行在 8081 端口，后端运行在 8080 端口时，需要配置 CORS
     * - CORS 配置告诉浏览器：允许哪些来源的请求可以访问后端
     */
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        String[] origins = "*".equals(allowedOrigins)
                ? new String[]{"*"}
                : allowedOrigins.split(",");
        boolean allowCredentials = !"*".equals(allowedOrigins);
        registry.addMapping("/**")
                .allowedOriginPatterns(origins)
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(allowCredentials)
                .maxAge(3600);
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 限流拦截器：所有请求都需要限流
        registry.addInterceptor(rateLimitInterceptor)
                .addPathPatterns("/api/**");

        // JWT 拦截器：需要 token 的接口才拦截
        registry.addInterceptor(jwtInterceptor)
                .addPathPatterns("/api/**")
                .excludePathPatterns(
                        "/api/auth/register",
                        "/api/auth/login",
                        "/static/**"
                );
    }

    /**
     * 配置静态资源映射
     * 将 /avatars/** 请求映射到实际的头像上传目录
     */
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        Path avatarPath = Paths.get(avatarUploadPath).toAbsolutePath().normalize();
        String avatarLocation = "file:" + avatarPath.toString() + "/";
        log.info("Avatar upload path: {}", avatarLocation);
        registry.addResourceHandler("/avatars/**")
                .addResourceLocations(avatarLocation);

        Path filePath = Paths.get(fileUploadPath).toAbsolutePath().normalize();
        String fileLocation = "file:" + filePath.toString() + "/";
        log.info("File upload path: {}", fileLocation);
        registry.addResourceHandler("/uploads/**")
                .addResourceLocations(fileLocation);
    }
}