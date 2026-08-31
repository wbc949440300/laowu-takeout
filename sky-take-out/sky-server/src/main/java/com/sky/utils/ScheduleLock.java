package com.sky.utils;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.TimeUnit;

/**
 * 定时任务分布式锁：基于 Redis SETNX，防止多实例部署时 @Scheduled 任务重复执行
 * 用法：任务开始时 tryLock，拿到锁才执行业务；锁到期自动释放（无需手动解锁，避免误删他人锁）
 */
@Component
@Slf4j
public class ScheduleLock {

    private static final String KEY_PREFIX = "schedule:lock:";

    @Autowired
    private RedisTemplate redisTemplate;

    /**
     * 尝试获取任务锁
     *
     * @param taskName   任务名（作锁标识）
     * @param expireSeconds 锁有效期，应略小于任务触发间隔
     * @return true=抢到锁，可以执行
     */
    public boolean tryLock(String taskName, long expireSeconds) {
        try {
            Boolean locked = (Boolean) redisTemplate.opsForValue()
                    .setIfAbsent(KEY_PREFIX + taskName, "1", expireSeconds, TimeUnit.SECONDS);
            return Boolean.TRUE.equals(locked);
        } catch (Exception e) {
            //Redis 异常时放行任务（与限流组件同样策略：防护组件故障不阻断核心业务）
            log.error("Failed to acquire scheduled-task lock: {}", taskName, e);
            return false;
        }
    }
}
