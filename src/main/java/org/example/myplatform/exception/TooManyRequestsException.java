package org.example.myplatform.exception;

/**
 * 请求过于频繁异常
 * 当用户在短时间内发送过多请求时抛出
 * 用于限制系统资源被滥用
 */
public class TooManyRequestsException extends RuntimeException {
    public TooManyRequestsException(String message) {
        super(message);
    }
}