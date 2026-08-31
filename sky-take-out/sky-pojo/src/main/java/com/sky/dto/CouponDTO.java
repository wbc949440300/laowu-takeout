package com.sky.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 管理端创建/编辑优惠券
 */
@Data
public class CouponDTO implements Serializable {

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

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime startTime;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime endTime;

    //状态 0下架 1上架
    private Integer status;
}
