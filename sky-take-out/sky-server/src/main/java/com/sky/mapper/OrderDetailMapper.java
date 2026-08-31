package com.sky.mapper;

import com.sky.entity.OrderDetail;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface OrderDetailMapper {
    /**
     * 批量插入订单明细数据
     * @param orderDetailList
     */
    void insertBatch(List<OrderDetail> orderDetailList);

    /**
     * 根据订单id查询订单明细
     * @param orderId
     * @return
     */
    @Select("select * from order_detail where order_id = #{orderId}")
    List<OrderDetail> getByOrderId(Long orderId);

    /**
     * 根据订单id集合批量查询订单明细（消除分页查询的 N+1 问题）
     * @param orderIds
     * @return
     */
    @Select("<script>select * from order_detail where order_id in " +
            "<foreach collection='orderIds' item='id' open='(' separator=',' close=')'>#{id}</foreach>" +
            "</script>")
    List<OrderDetail> getByOrderIds(@Param("orderIds") List<Long> orderIds);
}
