package com.sky.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 退款申请
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RefundApply implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 审核状态 0待审核 1已同意 2已拒绝
     */
    public static final Integer PENDING = 0;
    public static final Integer APPROVED = 1;
    public static final Integer REJECTED = 2;

    private Long id;

    //订单id
    private Long orderId;

    //申请用户id
    private Long userId;

    //退款原因
    private String reason;

    //状态 0待审核 1已同意 2已拒绝
    private Integer status;

    //处理备注
    private String handleRemark;

    //申请时间
    private LocalDateTime createTime;

    //处理时间
    private LocalDateTime handleTime;
}
