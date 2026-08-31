package com.sky.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 配送员（骑手）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeliveryStaff implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;

    //姓名
    private String name;

    //手机号
    private String phone;

    //状态 0停用 1在职
    private Integer status;

    //创建时间
    private LocalDateTime createTime;
}
