package com.sky.mapper;

import com.sky.entity.ReminderRecord;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface ReminderRecordMapper {

    /**
     * 插入催单记录
     * @param reminderRecord
     */
    @Insert("insert into reminder_record (order_id, user_id, create_time) " +
            "values (#{orderId}, #{userId}, #{createTime})")
    void insert(ReminderRecord reminderRecord);

    /**
     * 根据订单id查询催单记录
     * @param orderId
     */
    @Select("select * from reminder_record where order_id = #{orderId} order by create_time desc")
    List<ReminderRecord> getByOrderId(Long orderId);
}
