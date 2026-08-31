package com.sky.service;

import com.sky.constant.MessageConstant;
import com.sky.entity.Orders;
import com.sky.exception.OrderBusinessException;

/** Centralized legal order transitions shared by controllers, jobs and callbacks. */
public final class OrderStateMachine {
    private OrderStateMachine() { }

    public static void require(Orders order, Integer target) {
        if (order == null || !isAllowed(order.getStatus(), target)) {
            throw new OrderBusinessException(MessageConstant.ORDER_STATUS_ERROR);
        }
    }

    public static boolean isAllowed(Integer source, Integer target) {
        if (source == null || target == null) return false;
        if (Orders.CONFIRMED.equals(target)) return Orders.TO_BE_CONFIRMED.equals(source);
        if (Orders.DELIVERY_IN_PROGRESS.equals(target)) return Orders.CONFIRMED.equals(source);
        if (Orders.COMPLETED.equals(target)) return Orders.DELIVERY_IN_PROGRESS.equals(source);
        if (Orders.CANCELLED.equals(target)) {
            return Orders.PENDING_PAYMENT.equals(source) || Orders.TO_BE_CONFIRMED.equals(source);
        }
        return false;
    }
}
