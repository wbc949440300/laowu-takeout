package com.sky.aspect;

import com.alibaba.fastjson.JSON;
import com.sky.context.BaseContext;
import com.sky.entity.AuditLog;
import com.sky.mapper.AuditLogMapper;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.After;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;

/**
 * 管理端写操作审计切面：记录谁在何时调用了哪个变更接口及参数
 * 仅审计 POST/PUT/DELETE（查询类不审计，避免日志膨胀）；审计失败不影响业务
 */
@Aspect
@Component
@Slf4j
public class AuditLogAspect {

    private static final int MAX_PARAMS_LENGTH = 1000;

    @Autowired
    private AuditLogMapper auditLogMapper;

    /**
     * 切入点：管理端控制器中所有写操作（POST/PUT/DELETE）
     */
    @Pointcut("execution(* com.sky.controller.admin..*.*(..)) && ("
            + "@annotation(org.springframework.web.bind.annotation.PostMapping)"
            + " || @annotation(org.springframework.web.bind.annotation.PutMapping)"
            + " || @annotation(org.springframework.web.bind.annotation.DeleteMapping))")
    public void auditPointCut() {
    }

    /**
     * 后置通知：方法执行后落审计记录（含执行失败场景，失败操作同样需要留痕）
     */
    @After("auditPointCut()")
    public void audit(JoinPoint joinPoint) {
        try {
            ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attributes == null) {
                return;
            }
            HttpServletRequest request = attributes.getRequest();

            //参数序列化，失败或超长时降级处理，避免审计反噬业务
            String params;
            try {
                params = JSON.toJSONString(joinPoint.getArgs());
            } catch (Exception e) {
                params = "参数序列化失败";
            }
            if (params != null && params.length() > MAX_PARAMS_LENGTH) {
                params = params.substring(0, MAX_PARAMS_LENGTH);
            }

            auditLogMapper.insert(AuditLog.builder()
                    .empId(BaseContext.getCurrentId())
                    .uri(request.getRequestURI())
                    .httpMethod(request.getMethod())
                    .params(params)
                    .createTime(LocalDateTime.now())
                    .build());
        } catch (Exception e) {
            log.error("审计日志写入失败：", e);
        }
    }
}
