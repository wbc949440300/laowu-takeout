package com.sky.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 用户优惠券
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserCoupon implements Serializable {

    private static final long serialVersionUID = 1L;

    //状态：0未使用 1已使用 2已过期
    public static final Integer UNUSED = 0;
    public static final Integer USED = 1;
    public static final Integer EXPIRED = 2;

    private Long id;

    //优惠券id
    private Long couponId;

    //用户id
    private Long userId;

    //状态 0未使用 1已使用 2已过期
    private Integer status;

    //核销订单id
    private Long orderId;

    //领取时间
    private LocalDateTime createTime;

    //使用时间
    private LocalDateTime usedTime;
}
