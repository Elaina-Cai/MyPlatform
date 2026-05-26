package org.example.myplatform.config;
import org.example.myplatform.exception.UnauthorizedException;
import org.example.myplatform.exception.TooManyRequestsException;
import org.example.myplatform.vo.Result;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import java.util.StringJoiner;
/**
 * 全局异常处理器
 *
 * 功能：
 * - 统一处理 Controller 抛出的异常
 * - 返回统一的 Result 格式
 *
 * 异常处理映射：
 * - UnauthorizedException → 401 未授权（认证失败）
 * - TooManyRequestsException → 429 请求过于频繁（限流）
 * * - MethodArgumentNotValidException → 422 参数校验失败
 * * - RuntimeException → 400 业务异常
 * - Exception → 500 系统异常（兜底）
 */
@RestControllerAdvice
public class GlobalExceptionHandler {
    /**
     * 处理未授权异常
     *
     * @param ex UnauthorizedException（token 验证失败时抛出）
     * @return 401 状态码 + 错误消息
     *
     * 使用场景：
     * - 未携带 token
     * - token 无效或已过期
     * - token 在黑名单中
     * - 登录已超时
     */
    @ExceptionHandler(UnauthorizedException.class)
    public Result<Void> handleUnauthorized(UnauthorizedException ex) {
        return Result.error(401, ex.getMessage());
    }
    /**
     * 处理请求过于频繁异常
     * @param ex TooManyRequestsException（请求过于频繁时抛出）
     * @return 429 状态码 + 错误消息
     */
    @ExceptionHandler(TooManyRequestsException.class)
    public Result<Void> handleTooManyRequests(TooManyRequestsException ex) {
        return Result.error(429, ex.getMessage());
    }
    /**
     * 处理参数校验异常
     *
     * @param ex 参数校验异常（@Valid 校验失败时抛出）
     * @return 422 状态码 + 所有校验错误信息拼接
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result<Void> handleValidation(MethodArgumentNotValidException ex) {
        StringJoiner joiner = new StringJoiner(",");
        for (FieldError error : ex.getBindingResult().getFieldErrors()) {
            joiner.add(error.getDefaultMessage());
        }
        return Result.error(422, joiner.toString());
    }
    /**
     * 处理业务异常
     *
     * @param ex 业务异常（如"用户名已存在"、"用户名或密码错误"）
     * @return 400 状态码 + 异常消息
     */
    @ExceptionHandler(RuntimeException.class)
    public Result<Void> handleRuntime(RuntimeException ex) {
        return Result.error(400, ex.getMessage());
    }
    /**
     * 处理系统异常（兜底）
     *
     * @param ex 其他未捕获的异常
     * @return 500 状态码 + 友好提示
     */
    @ExceptionHandler(Exception.class)
    public Result<Void> handleGeneral(Exception ex) {
        return Result.error(500, "系统异常，请稍后重试");
    }
}