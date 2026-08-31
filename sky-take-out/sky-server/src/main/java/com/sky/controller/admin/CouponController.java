package com.sky.controller.admin;

import com.sky.dto.CouponDTO;
import com.sky.entity.Coupon;
import com.sky.result.Result;
import com.sky.service.CouponService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController("adminCouponController")
@RequestMapping("/admin/coupon")
@Api(tags = "优惠券管理接口")
@Slf4j
public class CouponController {

    @Autowired
    private CouponService couponService;

    /**
     * 新增优惠券
     */
    @PostMapping
    @ApiOperation("新增优惠券")
    public Result save(@RequestBody CouponDTO couponDTO) {
        log.info("新增优惠券：{}", couponDTO);
        couponService.save(couponDTO);
        return Result.success();
    }

    /**
     * 优惠券列表
     */
    @GetMapping("/list")
    @ApiOperation("优惠券列表")
    public Result<List<Coupon>> list() {
        return Result.success(couponService.listAll());
    }

    /**
     * 上下架优惠券
     * @param status 0下架 1上架
     */
    @PostMapping("/status/{status}")
    @ApiOperation("上下架优惠券")
    public Result startOrStop(@PathVariable("status") Integer status, Long id) {
        log.info("上下架优惠券：{},{}", status, id);
        couponService.startOrStop(status, id);
        return Result.success();
    }
}
