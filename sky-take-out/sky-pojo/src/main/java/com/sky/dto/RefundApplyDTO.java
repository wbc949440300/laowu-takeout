package com.sky.dto;

import lombok.Data;
import java.io.Serializable;

/**
 * 用户退款申请
 */
@Data
public class RefundApplyDTO implements Serializable {

    //退款原因
    private String reason;
}
