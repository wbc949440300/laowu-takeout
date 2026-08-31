package com.sky.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 接口限流注解：基于 Redis 的固定窗口计数限流
 * 被标注的接口在 period 秒内，同一限流主体最多访问 limit 次
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RateLimit {

    /**
     * 限流规则名前缀，最终 Redis key = rl:{prefix}:{主体标识}
     */
    String prefix();

    /**
     * 时间窗口内允许的最大请求次数
     */
    int limit() default 10;

    /**
     * 时间窗口，单位秒
     */
    int period() default 60;

    /**
     * 限流主体：IP 按客户端 IP（登录接口等无登录态场景），
     * USER 按当前登录用户/员工 ID（登录后接口，更精准）
     */
    KeyType keyType() default KeyType.IP;

    enum KeyType {
        IP, USER
    }
}
