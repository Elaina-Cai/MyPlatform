package org.example.myplatform.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("user")
public class User {
    @TableId(type = IdType.AUTO)
    private Long id;          //用户ID
    private String username;  //登录所用账号，唯一
    private String password;  //密码
    private String phone;     //手机号，TODO 后续想要支持手机号登录和注册
    private String nickname;  //用户可自定义的昵称
    private String avatar;    //头像URL
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
