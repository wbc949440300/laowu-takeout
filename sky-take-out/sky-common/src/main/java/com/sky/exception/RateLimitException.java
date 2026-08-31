package com.sky.exception;

/**
 * 请求限流异常：触发频率限制时抛出，携带结构化错误码 4290，供前端/Agent 程序化重试决策
 */
public class RateLimitException extends BaseException {

    public static final Integer CODE = 4290;

    public RateLimitException(String msg) {
        super(msg, CODE);
    }
}
