package com.sky.vo;

import com.sky.entity.OrderDetail;
import com.sky.entity.Orders;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.io.Serializable;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class OrderVO extends Orders implements Serializable {

    //订单菜品信息
    private String orderDishes;

    //订单详情（兼容课程小程序读取的字段名 orderDetails）
    private List<OrderDetail> orderDetails;

    //订单详情（原有字段，保留兼容）
    private List<OrderDetail> orderDetailList;

}
