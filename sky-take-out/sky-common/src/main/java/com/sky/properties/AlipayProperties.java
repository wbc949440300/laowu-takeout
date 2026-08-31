package com.sky.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 支付宝支付配置（在 application.yml 的 sky.alipay 下配置）
 */
@Component
@ConfigurationProperties(prefix = "sky.alipay")
@Data
public class AlipayProperties {

    private String appId; //应用APPID
    private String privateKey; //应用私钥
    private String alipayPublicKey; //支付宝公钥
    private String gatewayUrl; //网关地址（沙箱：https://openapi.alipaydev.com/gateway.do）
    private String notifyUrl; //支付成功异步回调地址
    private String returnUrl; //支付后同步跳转地址

    /**
     * 是否已完成必要配置（未配置或仍是占位符时，支付接口给出明确提示，而不是空逻辑）
     */
    public boolean isConfigured() {
        return valid(appId) && valid(privateKey) && valid(alipayPublicKey);
    }

    private boolean valid(String value) {
        return value != null && !value.isEmpty() && !value.startsWith("your-");
    }
}
