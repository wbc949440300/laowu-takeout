package com.sky.mapper;

import com.sky.entity.OrderTimeline;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface OrderTimelineMapper {

    /**
     * 插入时间线事件
     * @param orderTimeline
     */
    @Insert("insert into order_timeline (order_id, event_type, remark, create_time) " +
            "values (#{orderId}, #{eventType}, #{remark}, #{createTime})")
    void insert(OrderTimeline orderTimeline);

    /**
     * 根据订单id查询时间线（按时间正序）
     * @param orderId
     */
    @Select("select * from order_timeline where order_id = #{orderId} order by create_time asc, id asc")
    List<OrderTimeline> getByOrderId(Long orderId);
}
