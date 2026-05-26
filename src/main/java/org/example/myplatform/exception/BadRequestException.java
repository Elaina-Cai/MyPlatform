package org.example.myplatform.exception;

/**
 * 错误请求异常类
 * 用于处理客户端请求参数错误的情况
 */
public class BadRequestException extends RuntimeException {

    public BadRequestException(String message) {
        super(message);
    }

    public BadRequestException(String message, Throwable cause) {
        super(message, cause);
    }
}