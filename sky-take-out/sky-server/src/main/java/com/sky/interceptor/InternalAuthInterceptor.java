package com.sky.interceptor;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 内部服务鉴权拦截器：仅放行携带正确 X-Internal-Key 的请求（供 sky-agent 等内部服务调用）
 * 生产环境建议叠加 IP 白名单
 */
@Component
@Slf4j
public class InternalAuthInterceptor implements HandlerInterceptor {

    public static final String HEADER_NAME = "X-Internal-Key";

    @Value("${sky.internal.key:}")
    private String internalKey;

    @Value("${sky.internal.allowed-ips:}")
    private String allowedIps;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String key = request.getHeader(HEADER_NAME);
        String remoteIp = request.getRemoteAddr();
        boolean ipAllowed = allowedIps == null || allowedIps.trim().isEmpty()
                || parseAllowedIps().stream().anyMatch(rule -> matchesIpRule(remoteIp, rule));
        if (internalKey.isEmpty() || !internalKey.equals(key) || !ipAllowed) {
            log.warn("内部接口鉴权失败：{} {}", request.getMethod(), request.getRequestURI());
            response.setStatus(401);
            response.setContentType("application/json;charset=utf-8");
            response.getWriter().write("{\"code\":0,\"msg\":\"内部接口鉴权失败\"}");
            return false;
        }
        log.info("内部接口鉴权通过：{} {}，来源IP={}，traceId={}",
                request.getMethod(), request.getRequestURI(), remoteIp, request.getHeader("X-Trace-Id"));
        return true;
    }

    private List<String> parseAllowedIps() {
        if (allowedIps == null || allowedIps.trim().isEmpty()) {
            return Collections.emptyList();
        }
        return Arrays.stream(allowedIps.split(","))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .collect(Collectors.toList());
    }

    private boolean matchesIpRule(String ip, String rule) {
        if (!rule.contains("/")) {
            return rule.equals(ip);
        }
        try {
            String[] parts = rule.split("/", 2);
            int prefix = Integer.parseInt(parts[1]);
            if (prefix < 0 || prefix > 32) {
                return false;
            }
            long mask = prefix == 0 ? 0 : (0xffffffffL << (32 - prefix)) & 0xffffffffL;
            return (ipv4ToLong(ip) & mask) == (ipv4ToLong(parts[0]) & mask);
        } catch (Exception ignored) {
            return false;
        }
    }

    private long ipv4ToLong(String ip) {
        String[] octets = ip.split("\\.");
        if (octets.length != 4) {
            throw new IllegalArgumentException("非 IPv4 地址");
        }
        long result = 0;
        for (String octet : octets) {
            int value = Integer.parseInt(octet);
            if (value < 0 || value > 255) {
                throw new IllegalArgumentException("IPv4 地址无效");
            }
            result = (result << 8) | value;
        }
        return result;
    }
}
