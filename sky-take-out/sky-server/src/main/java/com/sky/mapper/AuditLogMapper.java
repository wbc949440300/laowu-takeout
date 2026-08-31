package com.sky.mapper;

import com.sky.entity.AuditLog;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface AuditLogMapper {

    /**
     * 插入审计日志
     */
    @Insert("insert into audit_log (emp_id, uri, http_method, params, create_time) " +
            "values (#{empId}, #{uri}, #{httpMethod}, #{params}, #{createTime})")
    void insert(AuditLog auditLog);

    /**
     * 按员工查询审计日志（倒序）
     */
    @Select("select * from audit_log where emp_id = #{empId} order by create_time desc limit 200")
    List<AuditLog> listByEmpId(Long empId);
}
