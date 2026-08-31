package com.sky.service.impl;

import com.sky.context.BaseContext;
import com.sky.dto.CouponDTO;
import com.sky.entity.Coupon;
import com.sky.entity.UserCoupon;
import com.sky.exception.OrderBusinessException;
import com.sky.mapper.CouponMapper;
import com.sky.mapper.UserCouponMapper;
import com.sky.service.CouponService;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class CouponServiceImpl implements CouponService {

    @Autowired
    private CouponMapper couponMapper;
    @Autowired
    private UserCouponMapper userCouponMapper;

    /**
     * 管理端新增优惠券（含类型与参数合法性校验）
     */
    public void save(CouponDTO couponDTO) {
        if (couponDTO.getName() == null || couponDTO.getType() == null) {
            throw new OrderBusinessException("优惠券名称与类型不能为空");
        }
        if (Coupon.TYPE_FULL_REDUCTION.equals(couponDTO.getType()) && couponDTO.getDiscountAmount() == null) {
            throw new OrderBusinessException("满减券必须填写满减金额");
        }
        if (Coupon.TYPE_DISCOUNT.equals(couponDTO.getType()) && couponDTO.getDiscountRate() == null) {
            throw new OrderBusinessException("折扣券必须填写折扣率");
        }
        if (couponDTO.getStartTime() == null || couponDTO.getEndTime() == null
                || couponDTO.getStartTime().isAfter(couponDTO.getEndTime())) {
            throw new OrderBusinessException("请填写正确的生效/失效时间");
        }

        Coupon coupon = new Coupon();
        BeanUtils.copyProperties(couponDTO, coupon);
        coupon.setReceivedCount(0);
        coupon.setCreateTime(LocalDateTime.now());
        if (coupon.getTotalCount() == null) {
            coupon.setTotalCount(0);//0表示不限量
        }
        if (coupon.getStatus() == null) {
            coupon.setStatus(1);
        }
        couponMapper.insert(coupon);
    }

    /**
     * 管理端查询全部优惠券
     */
    public List<Coupon> listAll() {
        return couponMapper.listAll();
    }

    /**
     * 管理端上下架优惠券
     */
    public void startOrStop(Integer status, Long id) {
        if (couponMapper.getById(id) == null) {
            throw new OrderBusinessException("优惠券不存在");
        }
        couponMapper.updateStatus(id, status);
    }

    /**
     * 用户查询可领取的优惠券
     */
    public List<Coupon> listAvailable() {
        return couponMapper.listAvailable();
    }

    /**
     * 用户领取优惠券：上架+有效期内+每人限领1张；限量券用原子递增防超发
     */
    @Transactional
    public void receive(Long couponId) {
        Long userId = BaseContext.getCurrentId();

        Coupon coupon = couponMapper.getById(couponId);
        if (coupon == null || !Integer.valueOf(1).equals(coupon.getStatus())) {
            throw new OrderBusinessException("优惠券不存在或已下架");
        }
        LocalDateTime now = LocalDateTime.now();
        if (now.isBefore(coupon.getStartTime()) || now.isAfter(coupon.getEndTime())) {
            throw new OrderBusinessException("优惠券不在领取时间内");
        }

        //每人每券限领1张
        if (userCouponMapper.countByUserAndCoupon(userId, couponId) > 0) {
            throw new OrderBusinessException("您已领取过该优惠券");
        }

        //限量券原子递增已领数量，失败即已发完（并发安全）
        if (couponMapper.incrementReceivedCount(couponId) == 0) {
            throw new OrderBusinessException("优惠券已被领完");
        }

        userCouponMapper.insert(UserCoupon.builder()
                .couponId(couponId)
                .userId(userId)
                .status(UserCoupon.UNUSED)
                .createTime(now)
                .build());
    }

    /**
     * 查询当前用户的优惠券列表
     */
    public List<UserCoupon> myCoupons() {
        return userCouponMapper.listByUserId(BaseContext.getCurrentId());
    }
}
