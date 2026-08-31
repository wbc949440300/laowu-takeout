package com.sky.service;

import com.sky.entity.OrderTimeline;
import com.sky.entity.RefundApply;

import java.util.List;
import java.util.Map;

/**
 * 内部服务接口：供 sky-agent 等内部系统调用的聚合只读数据（需 X-Internal-Key 鉴权）
 */
public interface InternalService {

    /**
     * 查询用户最近订单（含明细），一次聚合返回，减少 Agent 多轮调用
     * @param userId 用户id
     * @param limit  条数（默认5）
     */
    List<Map<String, Object>> recentOrders(Long userId, Integer limit);

    /**
     * 查询订单时间线
     */
    List<OrderTimeline> orderTimeline(Long orderId);

    /**
     * 查询订单最新退款申请（无则返回 null）
     */
    RefundApply refundProgress(Long orderId);

    /**
     * 店铺信息聚合：营业开关、是否处于营业时段、营业时段文本
     */
    Map<String, Object> shopInfo();
}
