package com.sky.controller.user;

import com.sky.entity.Coupon;
import com.sky.entity.UserCoupon;
import com.sky.result.Result;
import com.sky.service.CouponService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController("userCouponController")
@RequestMapping("/user/coupon")
@Api(tags = "用户端优惠券相关接口")
@Slf4j
public class CouponController {

    @Autowired
    private CouponService couponService;

    /**
     * 查询可领取的优惠券
     */
    @GetMapping("/available")
    @ApiOperation("查询可领取的优惠券")
    public Result<List<Coupon>> available() {
        return Result.success(couponService.listAvailable());
    }

    /**
     * 领取优惠券
     */
    @PostMapping("/receive/{id}")
    @ApiOperation("领取优惠券")
    public Result receive(@PathVariable("id") Long id) {
        log.info("用户领取优惠券：{}", id);
        couponService.receive(id);
        return Result.success();
    }

    /**
     * 查询我的优惠券
     */
    @GetMapping("/mine")
    @ApiOperation("查询我的优惠券")
    public Result<List<UserCoupon>> mine() {
        return Result.success(couponService.myCoupons());
    }
}
