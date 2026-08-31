package com.sky.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 优惠券
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Coupon implements Serializable {

    private static final long serialVersionUID = 1L;

    //类型：1满减券 2折扣券
    public static final Integer TYPE_FULL_REDUCTION = 1;
    public static final Integer TYPE_DISCOUNT = 2;

    private Long id;

    //券名称
    private String name;

    //类型 1满减券 2折扣券
    private Integer type;

    //使用门槛金额
    private BigDecimal thresholdAmount;

    //满减金额（type=1）
    private BigDecimal discountAmount;

    //折扣率如0.85（type=2）
    private BigDecimal discountRate;

    //发行总量，0表示不限
    private Integer totalCount;

    //已领取数量
    private Integer receivedCount;

    //生效时间
    private LocalDateTime startTime;

    //失效时间
    private LocalDateTime endTime;

    //状态 0下架 1上架
    private Integer status;

    //创建时间
    private LocalDateTime createTime;
}
