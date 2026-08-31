package com.sky.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 操作审计日志：记录管理端写操作（谁在何时对哪个接口发起了什么变更）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditLog implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;

    //操作员工id（登录接口等无登录态场景为 null）
    private Long empId;

    //请求URI
    private String uri;

    //请求方法 POST/PUT/DELETE
    private String httpMethod;

    //请求参数（超长截断）
    private String params;

    //操作时间
    private LocalDateTime createTime;
}
