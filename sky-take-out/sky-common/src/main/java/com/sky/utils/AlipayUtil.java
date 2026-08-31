package com.sky.utils;

import com.alibaba.fastjson.JSONObject;
import com.alipay.api.AlipayClient;
import com.alipay.api.DefaultAlipayClient;
import com.alipay.api.request.AlipayTradePagePayRequest;
import com.alipay.api.request.AlipayTradeRefundRequest;
import com.alipay.api.response.AlipayTradePagePayResponse;
import com.alipay.api.response.AlipayTradeRefundResponse;
import com.sky.properties.AlipayProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * 支付宝支付工具：电脑网站支付（页面表单）+ 退款
 */
@Component
public class AlipayUtil {

    private static final String FORMAT = "json";
    private static final String CHARSET = "utf-8";
    private static final String SIGN_TYPE = "RSA2";
    //电脑网站支付产品码
    private static final String PRODUCT_CODE = "FAST_INSTANT_TRADE_PAY";

    @Autowired
    private AlipayProperties alipayProperties;

    private volatile AlipayClient alipayClient;

    /**
     * 懒加载支付宝客户端（双重检查，避免每次调用都新建连接）
     */
    private AlipayClient getClient() {
        if (alipayClient == null) {
            synchronized (this) {
                if (alipayClient == null) {
                    alipayClient = new DefaultAlipayClient(
                            alipayProperties.getGatewayUrl(),
                            alipayProperties.getAppId(),
                            alipayProperties.getPrivateKey(),
                            FORMAT,
                            CHARSET,
                            alipayProperties.getAlipayPublicKey(),
                            SIGN_TYPE);
                }
            }
        }
        return alipayClient;
    }

    /**
     * 电脑网站支付：返回自动提交的表单HTML，前端渲染后即跳转支付宝收银台
     *
     * @param outTradeNo  商户订单号
     * @param totalAmount 支付金额，单位 元
     * @param subject     订单标题
     */
    public String pagePay(String outTradeNo, BigDecimal totalAmount, String subject) throws Exception {
        AlipayTradePagePayRequest request = new AlipayTradePagePayRequest();
        request.setNotifyUrl(alipayProperties.getNotifyUrl());
        request.setReturnUrl(alipayProperties.getReturnUrl());

        JSONObject bizContent = new JSONObject();
        bizContent.put("out_trade_no", outTradeNo);
        bizContent.put("total_amount", totalAmount.setScale(2, BigDecimal.ROUND_HALF_UP).toString());
        bizContent.put("subject", subject);
        bizContent.put("product_code", PRODUCT_CODE);
        request.setBizContent(bizContent.toString());

        AlipayTradePagePayResponse response = getClient().pageExecute(request);
        if (!response.isSuccess()) {
            throw new RuntimeException("支付宝下单失败：" + response.getSubMsg());
        }
        return response.getBody();
    }

    /**
     * 退款（同步返回结果，无需回调确认）
     *
     * @param outTradeNo   商户订单号
     * @param outRequestNo 商户退款单号（部分退款时用于标识每笔退款）
     * @param refundAmount 退款金额，单位 元
     */
    public String refund(String outTradeNo, String outRequestNo, BigDecimal refundAmount) throws Exception {
        AlipayTradeRefundRequest request = new AlipayTradeRefundRequest();

        JSONObject bizContent = new JSONObject();
        bizContent.put("out_trade_no", outTradeNo);
        bizContent.put("refund_amount", refundAmount.setScale(2, BigDecimal.ROUND_HALF_UP).toString());
        bizContent.put("out_request_no", outRequestNo);
        request.setBizContent(bizContent.toString());

        AlipayTradeRefundResponse response = getClient().execute(request);
        if (!response.isSuccess()) {
            throw new RuntimeException("支付宝退款失败：" + response.getSubMsg());
        }
        return response.getBody();
    }
}
