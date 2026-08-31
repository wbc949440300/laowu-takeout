package com.sky.service;

import com.sky.dto.*;
import com.sky.entity.OrderTimeline;
import com.sky.entity.RefundApply;
import com.sky.result.PageResult;
import com.sky.vo.OrderPaymentVO;
import com.sky.vo.OrderStatisticsVO;
import com.sky.vo.OrderSubmitVO;
import com.sky.vo.OrderVO;

import java.util.List;

public interface OrderService {
    /**
     * 用户下单
     * @param ordersSubmitDTO
     * @return
     */
    OrderSubmitVO submitOrder(OrdersSubmitDTO ordersSubmitDTO);

    /**
     * 订单支付
     * @param ordersPaymentDTO
     * @return
     */
    OrderPaymentVO payment(OrdersPaymentDTO ordersPaymentDTO) throws Exception;

    /**
     * 支付成功，修改订单状态
     * @param outTradeNo
     */
    void paySuccess(String outTradeNo);

    /**
     * 用户端订单分页查询
     * @param page
     * @param pageSize
     * @param status
     * @return
     */
    PageResult pageQuery4User(int page, int pageSize, Integer status);

    /**
     * 查询订单详情（管理端，不校验归属）
     * @param id
     * @return
     */
    OrderVO details(Long id);

    /**
     * 用户端查询订单详情（校验订单归属，防越权）
     * @param id
     * @return
     */
    OrderVO details4User(Long id);

    /**
     * 退款成功回调处理，确认退款状态
     * @param outTradeNo 商户订单号
     * @param refundStatus 退款状态（SUCCESS/CHANGE/ABNORMAL）
     */
    void refundSuccess(String outTradeNo, String refundStatus);

    /**
     * 用户取消订单
     * @param id
     */
    void userCancelById(Long id) throws Exception;

    /**
     * 再来一单
     * @param id
     */
    void repetition(Long id);

    /**
     * 条件搜索订单
     * @param ordersPageQueryDTO
     * @return
     */
    PageResult conditionSearch(OrdersPageQueryDTO ordersPageQueryDTO);

    /**
     * 各个状态的订单数量统计
     * @return
     */
    OrderStatisticsVO statistics();

    /**
     * 接单
     *
     * @param ordersConfirmDTO
     */
    void confirm(OrdersConfirmDTO ordersConfirmDTO);

    /**
     * 拒单
     *
     * @param ordersRejectionDTO
     */
    void rejection(OrdersRejectionDTO ordersRejectionDTO) throws Exception;

    /**
     * 商家取消订单
     *
     * @param ordersCancelDTO
     */
    void cancel(OrdersCancelDTO ordersCancelDTO) throws Exception;

    /**
     * 派送订单（可选指派配送员）
     *
     * @param id
     * @param riderId 配送员id，可为空
     */
    void delivery(Long id, Long riderId);

    /**
     * 完成订单
     *
     * @param id
     */
    void complete(Long id);

    /**
     * 客户催单
     * @param id
     */
    void reminder(Long id);

    /**
     * 用户端查询订单时间线（校验归属）
     * @param orderId
     */
    List<OrderTimeline> timeline4User(Long orderId);

    /** Administrator order timeline query. */
    List<OrderTimeline> timeline(Long orderId);

    /**
     * 用户申请退款（已支付且未完成/未取消的订单）
     * @param orderId
     * @param refundApplyDTO
     */
    void applyRefund(Long orderId, RefundApplyDTO refundApplyDTO);

    /**
     * 用户查询退款进度（校验归属）
     * @param orderId
     */
    RefundApply getRefundProgress(Long orderId);

    /**
     * 管理端查询退款申请列表（状态可空=全部）
     * @param status
     */
    List<RefundApply> listRefundApply(Integer status);

    /**
     * 管理端处理退款申请（同意退款/拒绝）
     * @param refundHandleDTO
     */
    void handleRefund(RefundHandleDTO refundHandleDTO) throws Exception;
}
