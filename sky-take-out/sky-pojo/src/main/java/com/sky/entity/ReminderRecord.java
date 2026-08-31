package com.sky.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 催单记录
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReminderRecord implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;

    //订单id
    private Long orderId;

    //催单用户id
    private Long userId;

    //催单时间
    private LocalDateTime createTime;
}
