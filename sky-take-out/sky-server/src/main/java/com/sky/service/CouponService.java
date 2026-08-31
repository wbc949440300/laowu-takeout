package com.sky.service;

import com.sky.dto.CouponDTO;
import com.sky.entity.Coupon;
import com.sky.entity.UserCoupon;

import java.util.List;

public interface CouponService {

    /**
     * 管理端新增优惠券
     * @param couponDTO
     */
    void save(CouponDTO couponDTO);

    /**
     * 管理端查询全部优惠券
     */
    List<Coupon> listAll();

    /**
     * 管理端上下架优惠券
     * @param status 0下架 1上架
     * @param id
     */
    void startOrStop(Integer status, Long id);

    /**
     * 用户查询可领取的优惠券（上架且在有效期内）
     */
    List<Coupon> listAvailable();

    /**
     * 用户领取优惠券（每人每券限领1张，限量券原子防超发）
     * @param couponId
     */
    void receive(Long couponId);

    /**
     * 查询当前用户的优惠券列表
     */
    List<UserCoupon> myCoupons();
}
