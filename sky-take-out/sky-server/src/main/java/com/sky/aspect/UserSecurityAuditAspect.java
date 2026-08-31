package com.sky.aspect;

import com.sky.context.BaseContext;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.servlet.http.HttpServletRequest;

/** 用户登录和订单副作用接口的安全审计，不记录请求体及凭证。 */
@Aspect
@Component
@Slf4j
public class UserSecurityAuditAspect {

    @Pointcut("execution(* com.sky.controller.user.UserController.login(..))"
            + " || execution(* com.sky.controller.user.OrderController.cancel(..))"
            + " || execution(* com.sky.controller.user.OrderController.repetition(..))"
            + " || execution(* com.sky.controller.user.OrderController.reminder(..))"
            + " || execution(* com.sky.controller.user.OrderController.applyRefund(..))")
    public void securityOperation() {
    }

    @Around("securityOperation()")
    public Object audit(ProceedingJoinPoint joinPoint) throws Throwable {
        HttpServletRequest request = currentRequest();
        String uri = request == null ? "unknown" : request.getRequestURI();
        String ip = request == null ? "unknown" : request.getRemoteAddr();
        String traceId = request == null ? null : request.getHeader("X-Trace-Id");
        try {
            Object result = joinPoint.proceed();
            log.info("用户安全操作成功：uri={} userId={} ip={} traceId={}",
                    uri, BaseContext.getCurrentId(), ip, traceId);
            return result;
        } catch (Throwable error) {
            log.warn("用户安全操作失败：uri={} userId={} ip={} traceId={} error={}",
                    uri, BaseContext.getCurrentId(), ip, traceId, error.getClass().getSimpleName());
            throw error;
        }
    }

    private HttpServletRequest currentRequest() {
        ServletRequestAttributes attributes =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        return attributes == null ? null : attributes.getRequest();
    }
}
