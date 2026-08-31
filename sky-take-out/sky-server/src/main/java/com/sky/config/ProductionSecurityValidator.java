package com.sky.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import javax.annotation.PostConstruct;

/**
 * 生产环境启动保护：避免默认密钥、Mock 支付或占位凭证被误部署。
 */
@Configuration
@Profile("prod")
public class ProductionSecurityValidator {

    @Value("${sky.jwt.admin-secret-key:}")
    private String adminJwtSecret;

    @Value("${sky.jwt.user-secret-key:}")
    private String userJwtSecret;

    @Value("${sky.internal.key:}")
    private String internalKey;

    @Value("${sky.datasource.password:}")
    private String datasourcePassword;

    @Value("${sky.redis.password:}")
    private String redisPassword;

    @Value("${sky.wechat.mock-login:false}")
    private boolean mockLogin;

    @Value("${sky.wechat.mock-payment:false}")
    private boolean mockPayment;

    @Value("${sky.wechat.appid:}")
    private String wechatAppId;

    @Value("${sky.wechat.secret:}")
    private String wechatSecret;

    @Value("${sky.wechat.apiV3Key:}")
    private String wechatApiV3Key;

    @Value("${sky.internal.allowed-ips:}")
    private String internalAllowedIps;

    @PostConstruct
    public void validate() {
        requireStrong("sky.jwt.admin-secret-key", adminJwtSecret);
        requireStrong("sky.jwt.user-secret-key", userJwtSecret);
        requireStrong("sky.internal.key", internalKey);
        requireConfigured("sky.internal.allowed-ips", internalAllowedIps);
        rejectDefault("sky.datasource.password", datasourcePassword, "123456");
        rejectDefault("sky.redis.password", redisPassword, "123456");

        if (mockLogin || mockPayment) {
            throw new IllegalStateException("生产环境禁止启用微信 Mock 登录或 Mock 支付");
        }

        requireConfigured("sky.wechat.appid", wechatAppId);
        requireConfigured("sky.wechat.secret", wechatSecret);
        requireConfigured("sky.wechat.apiV3Key", wechatApiV3Key);
    }

    private void requireStrong(String name, String value) {
        requireConfigured(name, value);
        if (value.length() < 32) {
            throw new IllegalStateException(name + " 长度必须至少为 32 个字符");
        }
    }

    private void rejectDefault(String name, String value, String defaultValue) {
        requireConfigured(name, value);
        if (defaultValue.equals(value)) {
            throw new IllegalStateException(name + " 不能使用默认密码");
        }
    }

    private void requireConfigured(String name, String value) {
        if (value == null || value.trim().isEmpty() || value.startsWith("your-")) {
            throw new IllegalStateException(name + " 未配置有效生产值");
        }
    }
}
