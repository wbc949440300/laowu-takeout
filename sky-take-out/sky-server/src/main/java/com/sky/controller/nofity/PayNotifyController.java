package com.sky.controller.nofity;

import com.alibaba.druid.support.json.JSONUtils;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.alipay.api.internal.util.AlipaySignature;
import com.sky.properties.AlipayProperties;
import com.sky.properties.WeChatProperties;
import com.sky.service.OrderService;
import com.wechat.pay.contrib.apache.httpclient.util.AesUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.entity.ContentType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.BufferedReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * 支付回调相关接口
 */
@RestController
@RequestMapping("/notify")
@Slf4j
public class PayNotifyController {
    @Autowired
    private OrderService orderService;
    @Autowired
    private WeChatProperties weChatProperties;
    @Autowired
    private AlipayProperties alipayProperties;

    /**
     * 支付成功回调
     *
     * @param request
     */
    @RequestMapping("/paySuccess")
    public void paySuccessNotify(HttpServletRequest request, HttpServletResponse response) throws Exception {
        //读取数据
        String body = readData(request);
        log.info("支付成功回调：{}", body);

        //数据解密
        String plainText = decryptData(body);
        log.info("解密后的文本：{}", plainText);

        JSONObject jsonObject = JSON.parseObject(plainText);
        String outTradeNo = jsonObject.getString("out_trade_no");//商户平台订单号
        String transactionId = jsonObject.getString("transaction_id");//微信支付交易号

        log.info("商户平台订单号：{}", outTradeNo);
        log.info("微信支付交易号：{}", transactionId);

        //业务处理，修改订单状态、来单提醒
        orderService.paySuccess(outTradeNo);

        //给微信响应
        responseToWeixin(response);
    }

    /**
     * 支付宝支付成功异步回调（POST 表单，对应配置 sky.alipay.notify-url）
     */
    @PostMapping("/alipaySuccess")
    public void alipaySuccessNotify(HttpServletRequest request, HttpServletResponse response) throws Exception {
        //收集全部参数用于验签与业务处理
        Map<String, String> params = new HashMap<>();
        Map<String, String[]> requestParams = request.getParameterMap();
        for (Map.Entry<String, String[]> entry : requestParams.entrySet()) {
            params.put(entry.getKey(), String.join(",", entry.getValue()));
        }
        log.info("支付宝支付回调：{}", params);

        //验签，防止伪造回调（验签失败直接拒绝，不做业务处理）
        boolean verify = AlipaySignature.rsaCheckV1(params, alipayProperties.getAlipayPublicKey(), "utf-8", "RSA2");
        if (!verify) {
            log.warn("支付宝回调验签失败，已拒绝");
            response.getWriter().write("failure");
            return;
        }

        String outTradeNo = params.get("out_trade_no");//商户平台订单号
        String tradeStatus = params.get("trade_status");//交易状态
        log.info("商户平台订单号：{}，交易状态：{}", outTradeNo, tradeStatus);

        //仅在支付成功/交易完成时处理，复用现有支付成功逻辑（改状态+时间线+来单提醒，幂等保护重复通知）
        if ("TRADE_SUCCESS".equals(tradeStatus) || "TRADE_FINISHED".equals(tradeStatus)) {
            orderService.paySuccess(outTradeNo);
        }

        //给支付宝响应 success，避免重复推送
        response.getWriter().write("success");
    }

    /**
     * 退款成功回调（对应配置 refundNotifyUrl：/notify/refundSuccess）
     *
     * @param request
     */
    @RequestMapping("/refundSuccess")
    public void refundSuccessNotify(HttpServletRequest request, HttpServletResponse response) throws Exception {
        //读取数据
        String body = readData(request);
        log.info("退款成功回调：{}", body);

        //数据解密（与支付回调同结构：resource 密文）
        String plainText = decryptData(body);
        log.info("解密后的文本：{}", plainText);

        JSONObject jsonObject = JSON.parseObject(plainText);
        String outTradeNo = jsonObject.getString("out_trade_no");//商户平台订单号
        String outRefundNo = jsonObject.getString("out_refund_no");//商户退款单号
        String refundStatus = jsonObject.getString("refund_status");//退款状态 SUCCESS/CHANGE/ABNORMAL

        log.info("商户平台订单号：{}，商户退款单号：{}，退款状态：{}", outTradeNo, outRefundNo, refundStatus);

        //业务处理，确认退款状态（幂等）
        orderService.refundSuccess(outTradeNo, refundStatus);

        //给微信响应，避免重复推送
        responseToWeixin(response);
    }

    /**
     * 读取数据
     *
     * @param request
     * @return
     * @throws Exception
     */
    private String readData(HttpServletRequest request) throws Exception {
        BufferedReader reader = request.getReader();
        StringBuilder result = new StringBuilder();
        String line = null;
        while ((line = reader.readLine()) != null) {
            if (result.length() > 0) {
                result.append("\n");
            }
            result.append(line);
        }
        return result.toString();
    }

    /**
     * 数据解密
     *
     * @param body
     * @return
     * @throws Exception
     */
    private String decryptData(String body) throws Exception {
        JSONObject resultObject = JSON.parseObject(body);
        JSONObject resource = resultObject.getJSONObject("resource");
        String ciphertext = resource.getString("ciphertext");
        String nonce = resource.getString("nonce");
        String associatedData = resource.getString("associated_data");

        AesUtil aesUtil = new AesUtil(weChatProperties.getApiV3Key().getBytes(StandardCharsets.UTF_8));
        //密文解密
        String plainText = aesUtil.decryptToString(associatedData.getBytes(StandardCharsets.UTF_8),
                nonce.getBytes(StandardCharsets.UTF_8),
                ciphertext);

        return plainText;
    }

    /**
     * 给微信响应
     * @param response
     */
    private void responseToWeixin(HttpServletResponse response) throws Exception{
        response.setStatus(200);
        HashMap<Object, Object> map = new HashMap<>();
        map.put("code", "SUCCESS");
        map.put("message", "SUCCESS");
        response.setHeader("Content-type", ContentType.APPLICATION_JSON.toString());
        response.getOutputStream().write(JSONUtils.toJSONString(map).getBytes(StandardCharsets.UTF_8));
        response.flushBuffer();
    }
}
