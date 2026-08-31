package com.sky.mapper;

import com.sky.entity.Coupon;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface CouponMapper {

    /**
     * 新增优惠券
     */
    @Insert("insert into coupon (name, type, threshold_amount, discount_amount, discount_rate, " +
            "total_count, received_count, start_time, end_time, status, create_time) " +
            "values (#{name}, #{type}, #{thresholdAmount}, #{discountAmount}, #{discountRate}, " +
            "#{totalCount}, #{receivedCount}, #{startTime}, #{endTime}, #{status}, #{createTime})")
    void insert(Coupon coupon);

    /**
     * 根据id查询
     */
    @Select("select * from coupon where id = #{id}")
    Coupon getById(Long id);

    /**
     * 管理端查询全部优惠券（按创建时间倒序）
     */
    @Select("select * from coupon order by create_time desc")
    List<Coupon> listAll();

    /**
     * 查询上架且在有效期内的优惠券（用户可领取列表）
     */
    @Select("select * from coupon where status = 1 and start_time <= now() and end_time >= now() order by create_time desc")
    List<Coupon> listAvailable();

    /**
     * 更新上下架状态
     */
    @Update("update coupon set status = #{status} where id = #{id}")
    void updateStatus(Long id, Integer status);

    /**
     * 原子递增已领取数量（限量券防超发：仅当未达总量时成功）
     */
    @Update("update coupon set received_count = received_count + 1 " +
            "where id = #{id} and (total_count = 0 or received_count < total_count)")
    int incrementReceivedCount(Long id);
}
