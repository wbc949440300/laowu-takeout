package com.sky.controller.internal;

import com.sky.entity.OrderTimeline;
import com.sky.entity.RefundApply;
import com.sky.result.Result;
import com.sky.service.InternalService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 内部服务接口：供 sky-agent 等内部系统调用，统一由 InternalAuthInterceptor 做 X-Internal-Key 鉴权
 */
@RestController
@RequestMapping("/internal")
@Api(tags = "内部服务接口")
@Slf4j
public class InternalController {

    @Autowired
    private InternalService internalService;

    /**
     * 用户最近订单（含明细），聚合返回
     */
    @GetMapping("/user/{userId}/recent-orders")
    @ApiOperation("用户最近订单聚合查询")
    public Result<List<Map<String, Object>>> recentOrders(@PathVariable("userId") Long userId,
                                                          @RequestParam(value = "limit", required = false) Integer limit) {
        return Result.success(internalService.recentOrders(userId, limit));
    }

    /**
     * 订单时间线
     */
    @GetMapping("/order/{orderId}/timeline")
    @ApiOperation("订单时间线")
    public Result<List<OrderTimeline>> timeline(@PathVariable("orderId") Long orderId) {
        return Result.success(internalService.orderTimeline(orderId));
    }

    /**
     * 订单退款进度（最新一条退款申请）
     */
    @GetMapping("/order/{orderId}/refund")
    @ApiOperation("订单退款进度")
    public Result<RefundApply> refundProgress(@PathVariable("orderId") Long orderId) {
        return Result.success(internalService.refundProgress(orderId));
    }

    /**
     * 店铺信息聚合（营业开关/时段/是否营业中）
     */
    @GetMapping("/shop/info")
    @ApiOperation("店铺信息聚合")
    public Result<Map<String, Object>> shopInfo() {
        return Result.success(internalService.shopInfo());
    }
}
