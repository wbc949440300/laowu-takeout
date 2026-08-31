package com.sky.mapper;

import com.sky.entity.UserCoupon;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface UserCouponMapper {

    /**
     * 领取优惠券
     */
    @Insert("insert into user_coupon (coupon_id, user_id, status, create_time) " +
            "values (#{couponId}, #{userId}, #{status}, #{createTime})")
    void insert(UserCoupon userCoupon);

    /**
     * 根据id查询
     */
    @Select("select * from user_coupon where id = #{id}")
    UserCoupon getById(Long id);

    /**
     * 查询用户的优惠券列表（按领取时间倒序）
     */
    @Select("select * from user_coupon where user_id = #{userId} order by create_time desc")
    List<UserCoupon> listByUserId(Long userId);

    /**
     * 统计用户对某券的领取数量（用于限领校验）
     */
    @Select("select count(id) from user_coupon where user_id = #{userId} and coupon_id = #{couponId}")
    int countByUserAndCoupon(Long userId, Long couponId);

    /**
     * 核销：标记已使用并关联订单
     */
    @Update("update user_coupon set status = 1, order_id = #{orderId}, used_time = #{usedTime} " +
            "where id = #{id} and status = 0")
    int markUsed(Long id, Long orderId, java.time.LocalDateTime usedTime);
}
