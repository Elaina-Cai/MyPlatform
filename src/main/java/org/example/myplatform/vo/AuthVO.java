package org.example.myplatform.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
@Data
@NoArgsConstructor
@AllArgsConstructor
/**
 * 认证模块的api向前端返回的Result类里的data的数据类型
 */
public class AuthVO {
    private String token;
    private Long userId;
    private String username;
    private String nickname;
    private String avatar;
}