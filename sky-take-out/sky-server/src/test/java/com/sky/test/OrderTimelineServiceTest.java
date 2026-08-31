package com.sky.test;

import com.sky.constant.MessageConstant;
import com.sky.entity.OrderTimeline;
import com.sky.entity.Orders;
import com.sky.exception.OrderBusinessException;
import com.sky.mapper.OrderMapper;
import com.sky.mapper.OrderTimelineMapper;
import com.sky.service.impl.OrderServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderTimelineServiceTest {

    @Mock
    private OrderMapper orderMapper;

    @Mock
    private OrderTimelineMapper orderTimelineMapper;

    @InjectMocks
    private OrderServiceImpl orderService;

    @Test
    void returnsTimelineForExistingOrder() {
        Long orderId = 42L;
        Orders order = Orders.builder().id(orderId).build();
        List<OrderTimeline> timeline = Arrays.asList(
                OrderTimeline.builder().orderId(orderId).eventType(OrderTimeline.PLACED).build(),
                OrderTimeline.builder().orderId(orderId).eventType(OrderTimeline.PAID).build()
        );
        when(orderMapper.getById(orderId)).thenReturn(order);
        when(orderTimelineMapper.getByOrderId(orderId)).thenReturn(timeline);

        List<OrderTimeline> result = orderService.timeline(orderId);

        assertSame(timeline, result);
        assertEquals(2, result.size());
        verify(orderTimelineMapper).getByOrderId(orderId);
    }

    @Test
    void rejectsMissingOrder() {
        Long orderId = 404L;
        when(orderMapper.getById(orderId)).thenReturn(null);

        OrderBusinessException exception = assertThrows(
                OrderBusinessException.class,
                () -> orderService.timeline(orderId)
        );

        assertEquals(MessageConstant.ORDER_NOT_FOUND, exception.getMessage());
    }
}
