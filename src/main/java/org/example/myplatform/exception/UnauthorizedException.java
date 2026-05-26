package org.example.myplatform.exception;

/**
 * 未授权异常的异常类，是异常的一种类别，可以被new然后抛出
 *
 * 当 token 验证失败时抛出：
 * - 未携带 token
 * - token 无效或已过期
 * - token 在黑名单中
 * - 登录已超时（30分钟无操作）
 *
 * 由 GlobalExceptionHandler 统一处理，返回 401 状态码
 */
public class UnauthorizedException extends RuntimeException {

    public UnauthorizedException(String message) {
        super(message);
    }
}