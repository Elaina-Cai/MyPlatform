package org.example.myplatform;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@MapperScan("org.example.myplatform.mapper")
@EnableScheduling  // 添加这行启用定时任务
public class MyPlatformApplication {
    public static void main(String[] args) {
        SpringApplication.run(MyPlatformApplication.class, args);
    }
}