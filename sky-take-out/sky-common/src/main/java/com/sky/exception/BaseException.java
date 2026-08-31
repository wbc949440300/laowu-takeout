package com.sky.exception;

/**
 * 业务异常（支持结构化错误码：前端/Agent 可按 code 程序化处理）
 */
public class BaseException extends RuntimeException {

    //结构化错误码，为 null 时由全局异常处理器统一处理（返回 code=0）
    private Integer code;

    public BaseException() {
    }

    public BaseException(String msg) {
        super(msg);
    }

    public BaseException(String msg, Integer code) {
        super(msg);
        this.code = code;
    }

    public Integer getCode() {
        return code;
    }
}
