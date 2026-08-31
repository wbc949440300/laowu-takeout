package com.sky.test;

import com.sky.entity.Orders;
import com.sky.service.OrderStateMachine;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class OrderStateMachineTest {
    @Test
    void allowsOnlyLegalForwardTransitions() {
        assertTrue(OrderStateMachine.isAllowed(Orders.TO_BE_CONFIRMED, Orders.CONFIRMED));
        assertTrue(OrderStateMachine.isAllowed(Orders.CONFIRMED, Orders.DELIVERY_IN_PROGRESS));
        assertTrue(OrderStateMachine.isAllowed(Orders.DELIVERY_IN_PROGRESS, Orders.COMPLETED));
        assertFalse(OrderStateMachine.isAllowed(Orders.PENDING_PAYMENT, Orders.COMPLETED));
        assertFalse(OrderStateMachine.isAllowed(Orders.COMPLETED, Orders.CANCELLED));
    }
}
