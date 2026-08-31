package com.sky.aspect;

import com.sky.annotation.RateLimit;
import com.sky.constant.MessageConstant;
import com.sky.context.BaseContext;
import com.sky.exception.RateLimitException;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.servlet.http.HttpServletRequest;
import java.util.concurrent.TimeUnit;

/**
 * 接口限流切面：基于 Redis 固定窗口计数
 * 在标注 @RateLimit 的接口执行前计数，超限抛出 RateLimitException
 */
@Aspect
@Component
@Slf4j
public class RateLimitAspect {

    @Autowired
    private RedisTemplate redisTemplate;

    /**
     * 切入点：controller 包下所有标注 @RateLimit 的方法
     */
    @Pointcut("execution(* com.sky.controller..*.*(..)) && @annotation(com.sky.annotation.RateLimit)")
    public void rateLimitPointCut() {
    }

    /**
     * 前置通知：计数并判断是否超限
     */
    @Before("rateLimitPointCut()")
    public void rateLimit(JoinPoint joinPoint) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        RateLimit rateLimit = signature.getMethod().getAnnotation(RateLimit.class);

        //确定限流主体标识
        String subject;
        if (rateLimit.keyType() == RateLimit.KeyType.USER) {
            Long currentId = BaseContext.getCurrentId();
            //登录态缺失时降级按 IP，避免空 key 导致所有请求共用一个计数器
            subject = currentId == null ? "ip-" + getIp() : "u" + currentId;
        } else {
            subject = "ip-" + getIp();
        }

        String key = "rl:" + rateLimit.prefix() + ":" + subject;
        try {
            Long count = (Long) redisTemplate.opsForValue().increment(key);
            if (count != null && count == 1) {
                //首次计数，设置窗口过期时间
                redisTemplate.expire(key, rateLimit.period(), TimeUnit.SECONDS);
            }
            if (count != null && count > rateLimit.limit()) {
                log.warn("接口限流触发：{}，当前计数：{}，限制：{}/{}秒", key, count, rateLimit.limit(), rateLimit.period());
                throw new RateLimitException(MessageConstant.REQUEST_TOO_FREQUENT);
            }
        } catch (RateLimitException e) {
            throw e;
        } catch (Exception e) {
            //Redis 异常时放行（限流是防护手段，不能因限流组件故障阻断核心业务）
            log.error("限流组件异常，本次放行：", e);
        }
    }

    /**
     * 获取客户端真实 IP（兼容反向代理场景）
     */
    private String getIp() {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            return "unknown";
        }
        HttpServletRequest request = attributes.getRequest();
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("X-Real-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        //X-Forwarded-For 可能包含多级代理的多个 IP，取第一个
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip;
    }
}
