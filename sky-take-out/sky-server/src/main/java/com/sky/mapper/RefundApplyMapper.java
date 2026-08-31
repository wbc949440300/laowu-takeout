package com.sky.mapper;

import com.sky.entity.RefundApply;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface RefundApplyMapper {

    /**
     * 插入退款申请
     * @param refundApply
     */
    @Insert("insert into refund_apply (order_id, user_id, reason, status, create_time) " +
            "values (#{orderId}, #{userId}, #{reason}, #{status}, #{createTime})")
    void insert(RefundApply refundApply);

    /**
     * 根据id查询退款申请
     * @param id
     */
    @Select("select * from refund_apply where id = #{id}")
    RefundApply getById(Long id);

    /**
     * 查询订单最新的一条退款申请
     * @param orderId
     */
    @Select("select * from refund_apply where order_id = #{orderId} order by create_time desc limit 1")
    RefundApply getLatestByOrderId(Long orderId);

    /**
     * 按状态查询退款申请列表（管理端审核，按申请时间倒序）
     * @param status 可为 null 表示全部
     */
    @Select("<script>select * from refund_apply " +
            "<where><if test='status != null'>status = #{status}</if></where> " +
            "order by create_time desc</script>")
    List<RefundApply> list(Integer status);

    /**
     * 更新审核结果
     * @param refundApply
     */
    @Update("update refund_apply set status = #{status}, handle_remark = #{handleRemark}, handle_time = #{handleTime} where id = #{id}")
    void update(RefundApply refundApply);
}
