package com.sky.controller.user;

import com.sky.annotation.RateLimit;
import com.sky.dto.OrdersPaymentDTO;
import com.sky.dto.OrdersSubmitDTO;
import com.sky.dto.RefundApplyDTO;
import com.sky.entity.OrderTimeline;
import com.sky.entity.RefundApply;
import com.sky.result.PageResult;
import com.sky.result.Result;
import com.sky.service.OrderService;
import com.sky.vo.OrderPaymentVO;
import com.sky.vo.OrderSubmitVO;
import com.sky.vo.OrderVO;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController("userOrderController")
@RequestMapping("/user/order")
@Api(tags = "用户端订单相关接口")
@Slf4j
public class OrderController {

    @Autowired
    private OrderService orderService;

    /**
     * 用户下单
     * @param ordersSubmitDTO
     * @return
     */
    @PostMapping("/submit")
    @ApiOperation("用户下单")
    @RateLimit(prefix = "orderSubmit", limit = 5, period = 60, keyType = RateLimit.KeyType.USER)
    public Result<OrderSubmitVO> submit(@RequestBody OrdersSubmitDTO ordersSubmitDTO){
        log.info("用户下单，参数为：{}",ordersSubmitDTO);
        OrderSubmitVO orderSubmitVO = orderService.submitOrder(ordersSubmitDTO);
        return Result.success(orderSubmitVO);
    }

    /**
     * 订单支付
     *
     * @param ordersPaymentDTO
     * @return
     */
    @PutMapping("/payment")
    @ApiOperation("订单支付")
    public Result<OrderPaymentVO> payment(@RequestBody OrdersPaymentDTO ordersPaymentDTO) throws Exception {
        log.info("订单支付：{}", ordersPaymentDTO);
        OrderPaymentVO orderPaymentVO = orderService.payment(ordersPaymentDTO);
        log.info("生成预支付交易单：{}", orderPaymentVO);
        return Result.success(orderPaymentVO);
    }

    /**
     * 历史订单查询
     *
     * @param page
     * @param pageSize
     * @param status   订单状态 1待付款 2待接单 3已接单 4派送中 5已完成 6已取消
     * @return
     */
    @GetMapping("/historyOrders")
    @ApiOperation("历史订单查询")
    public Result<PageResult> page(int page, int pageSize, Integer status) {
        PageResult pageResult = orderService.pageQuery4User(page, pageSize, status);
        return Result.success(pageResult);
    }

    /**
     * 查询订单详情（校验订单归属，防越权）
     *
     * @param id
     * @return
     */
    @GetMapping("/orderDetail/{id}")
    @ApiOperation("查询订单详情")
    public Result<OrderVO> details(@PathVariable("id") Long id) {
        OrderVO orderVO = orderService.details4User(id);
        return Result.success(orderVO);
    }

    /**
     * 用户取消订单
     *
     * @return
     */
    @PutMapping("/cancel/{id}")
    @ApiOperation("取消订单")
    @RateLimit(prefix = "orderCancel", limit = 5, period = 60, keyType = RateLimit.KeyType.USER)
    public Result cancel(@PathVariable("id") Long id) throws Exception {
        orderService.userCancelById(id);
        return Result.success();
    }

    /**
     * 再来一单
     *
     * @param id
     * @return
     */
    @PostMapping("/repetition/{id}")
    @ApiOperation("再来一单")
    @RateLimit(prefix = "orderRepetition", limit = 10, period = 60, keyType = RateLimit.KeyType.USER)
    public Result repetition(@PathVariable Long id) {
        orderService.repetition(id);
        return Result.success();
    }

    /**
     * 客户催单
     * @param id
     * @return
     */
    @GetMapping("/reminder/{id}")
    @ApiOperation("客户催单")
    @RateLimit(prefix = "orderReminder", limit = 5, period = 60, keyType = RateLimit.KeyType.USER)
    public Result reminder(@PathVariable("id") Long id){
        orderService.reminder(id);
        return Result.success();
    }

    /**
     * 查询订单时间线（订单进度，校验归属）
     * @param id 订单id
     */
    @GetMapping("/timeline/{id}")
    @ApiOperation("查询订单时间线")
    public Result<List<OrderTimeline>> timeline(@PathVariable("id") Long id) {
        return Result.success(orderService.timeline4User(id));
    }

    /**
     * 用户申请退款（已支付且未完成/未取消的订单）
     * @param id 订单id
     */
    @PostMapping("/refund/apply/{id}")
    @ApiOperation("申请退款")
    @RateLimit(prefix = "refundApply", limit = 3, period = 60, keyType = RateLimit.KeyType.USER)
    public Result applyRefund(@PathVariable("id") Long id, @RequestBody RefundApplyDTO refundApplyDTO) {
        log.info("用户申请退款，订单id：{}，原因：{}", id, refundApplyDTO.getReason());
        orderService.applyRefund(id, refundApplyDTO);
        return Result.success();
    }

    /**
     * 查询退款进度（校验归属）
     * @param id 订单id
     */
    @GetMapping("/refund/progress/{id}")
    @ApiOperation("查询退款进度")
    public Result<RefundApply> refundProgress(@PathVariable("id") Long id) {
        return Result.success(orderService.getRefundProgress(id));
    }
}
