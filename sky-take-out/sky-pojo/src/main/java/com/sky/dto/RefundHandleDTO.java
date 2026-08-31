package com.sky.dto;

import lombok.Data;
import java.io.Serializable;

/**
 * 管理端退款审核
 */
@Data
public class RefundHandleDTO implements Serializable {

    //退款申请id
    private Long id;

    //是否同意：1同意并退款 2拒绝
    private Integer status;

    //处理备注（拒绝原因等）
    private String handleRemark;
}
