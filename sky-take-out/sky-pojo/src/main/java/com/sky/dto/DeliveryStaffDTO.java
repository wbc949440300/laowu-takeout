package com.sky.dto;

import lombok.Data;
import java.io.Serializable;

/**
 * 管理端新增配送员
 */
@Data
public class DeliveryStaffDTO implements Serializable {

    private Long id;

    //姓名
    private String name;

    //手机号
    private String phone;

    //状态 0停用 1在职
    private Integer status;
}
