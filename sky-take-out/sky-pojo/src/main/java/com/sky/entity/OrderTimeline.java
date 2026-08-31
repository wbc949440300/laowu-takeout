package com.sky.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 订单时间线：记录订单全生命周期事件，供用户端/客服查询订单进度
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderTimeline implements Serializable {

    private static final long serialVersionUID = 1L;

    //事件类型常量
    public static final String PLACED = "PLACED";           //下单
    public static final String PAID = "PAID";               //支付
    public static final String CONFIRMED = "CONFIRMED";     //接单
    public static final String DELIVERING = "DELIVERING";   //派送
    public static final String COMPLETED = "COMPLETED";     //完成
    public static final String CANCELLED = "CANCELLED";     //取消
    public static final String REFUND_APPLY = "REFUND_APPLY";//退款申请

    private Long id;

    //订单id
    private Long orderId;

    //事件类型
    private String eventType;

    //备注（取消原因等）
    private String remark;

    //事件时间
    private LocalDateTime createTime;
}
