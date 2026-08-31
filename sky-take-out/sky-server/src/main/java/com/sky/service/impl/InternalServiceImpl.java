package com.sky.service.impl;

import com.sky.entity.OrderDetail;
import com.sky.entity.OrderTimeline;
import com.sky.entity.Orders;
import com.sky.entity.RefundApply;
import com.sky.mapper.OrderDetailMapper;
import com.sky.mapper.OrderMapper;
import com.sky.mapper.OrderTimelineMapper;
import com.sky.mapper.RefundApplyMapper;
import com.sky.service.InternalService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 内部服务实现：聚合只读数据供 sky-agent 调用
 */
@Service
@Slf4j
public class InternalServiceImpl implements InternalService {

    private static final String SHOP_STATUS_KEY = "SHOP_STATUS";

    @Autowired
    private OrderMapper orderMapper;
    @Autowired
    private OrderDetailMapper orderDetailMapper;
    @Autowired
    private OrderTimelineMapper orderTimelineMapper;
    @Autowired
    private RefundApplyMapper refundApplyMapper;
    @Autowired
    private RedisTemplate redisTemplate;

    @Value("${sky.shop.open-time:08:00}")
    private String openTime;
    @Value("${sky.shop.close-time:22:00}")
    private String closeTime;

    /**
     * 用户最近订单（含明细）：批量查明细消除 N+1
     */
    public List<Map<String, Object>> recentOrders(Long userId, Integer limit) {
        int size = (limit == null || limit <= 0) ? 5 : Math.min(limit, 20);
        List<Orders> ordersList = orderMapper.listRecentByUserId(userId, size);

        List<Map<String, Object>> result = new ArrayList<>();
        if (ordersList == null || ordersList.isEmpty()) {
            return result;
        }

        List<Long> orderIds = ordersList.stream().map(Orders::getId).collect(Collectors.toList());
        Map<Long, List<OrderDetail>> detailMap = orderDetailMapper.getByOrderIds(orderIds).stream()
                .collect(Collectors.groupingBy(OrderDetail::getOrderId));

        for (Orders orders : ordersList) {
            Map<String, Object> item = new HashMap<>();
            item.put("id", orders.getId());
            item.put("number", orders.getNumber());
            item.put("status", orders.getStatus());
            item.put("payStatus", orders.getPayStatus());
            item.put("amount", orders.getAmount());
            item.put("orderTime", orders.getOrderTime());
            item.put("cancelReason", orders.getCancelReason());
            item.put("deliveryStaffName", orders.getDeliveryStaffName());
            item.put("details", detailMap.getOrDefault(orders.getId(), new ArrayList<>()).stream()
                    .map(detail -> {
                        Map<String, Object> summary = new HashMap<>();
                        summary.put("name", detail.getName());
                        summary.put("number", detail.getNumber());
                        summary.put("amount", detail.getAmount());
                        summary.put("dishFlavor", detail.getDishFlavor());
                        return summary;
                    }).collect(Collectors.toList()));
            result.add(item);
        }
        return result;
    }

    /**
     * 订单时间线
     */
    public List<OrderTimeline> orderTimeline(Long orderId) {
        return orderTimelineMapper.getByOrderId(orderId);
    }

    /**
     * 订单最新退款申请
     */
    public RefundApply refundProgress(Long orderId) {
        return refundApplyMapper.getLatestByOrderId(orderId);
    }

    /**
     * 店铺信息聚合：开关 + 时段判断（与用户端 /user/shop/status 规则一致）
     */
    public Map<String, Object> shopInfo() {
        Integer switchStatus = (Integer) redisTemplate.opsForValue().get(SHOP_STATUS_KEY);
        boolean inHours = inBusinessHours();
        boolean open = Integer.valueOf(1).equals(switchStatus) && inHours;

        Map<String, Object> info = new HashMap<>();
        info.put("switchStatus", switchStatus);
        info.put("inBusinessHours", inHours);
        info.put("open", open);
        info.put("hours", openTime + "-" + closeTime);
        return info;
    }

    private boolean inBusinessHours() {
        try {
            LocalTime now = LocalTime.now();
            LocalTime open = LocalTime.parse(openTime);
            LocalTime close = LocalTime.parse(closeTime);
            if (!open.isAfter(close)) {
                return !now.isBefore(open) && !now.isAfter(close);
            } else {
                //跨天时段，如 20:00-02:00
                return !now.isBefore(open) || !now.isAfter(close);
            }
        } catch (Exception e) {
            log.error("营业时段配置解析失败：{}-{}", openTime, closeTime, e);
            return true;
        }
    }
}
