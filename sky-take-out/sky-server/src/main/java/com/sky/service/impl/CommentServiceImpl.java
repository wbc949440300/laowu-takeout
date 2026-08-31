package com.sky.service.impl;

import com.sky.constant.MessageConstant;
import com.sky.context.BaseContext;
import com.sky.dto.CommentDTO;
import com.sky.entity.Comment;
import com.sky.entity.Orders;
import com.sky.exception.OrderBusinessException;
import com.sky.mapper.CommentMapper;
import com.sky.mapper.OrderMapper;
import com.sky.service.CommentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class CommentServiceImpl implements CommentService {

    @Autowired
    private CommentMapper commentMapper;
    @Autowired
    private OrderMapper orderMapper;

    /**
     * 用户提交订单评价：仅限本人已完成订单，一单一评，评分 1-5
     */
    public void submit(Long orderId, CommentDTO commentDTO) {
        Long userId = BaseContext.getCurrentId();

        //订单归属校验（防越权评价）
        Orders orders = orderMapper.getById(orderId);
        if (orders == null || !orders.getUserId().equals(userId)) {
            throw new OrderBusinessException(MessageConstant.ORDER_NOT_FOUND);
        }

        //仅已完成订单可评价
        if (!Orders.COMPLETED.equals(orders.getStatus())) {
            throw new OrderBusinessException("订单未完成，暂不能评价");
        }

        //一单一评（数据库唯一索引兜底）
        if (commentMapper.getByOrderId(orderId) != null) {
            throw new OrderBusinessException("该订单已评价，不能重复评价");
        }

        //评分范围校验
        if (commentDTO.getRating() == null || commentDTO.getRating() < 1 || commentDTO.getRating() > 5) {
            throw new OrderBusinessException("评分必须在1-5之间");
        }

        commentMapper.insert(Comment.builder()
                .orderId(orderId)
                .userId(userId)
                .rating(commentDTO.getRating())
                .content(commentDTO.getContent())
                .createTime(LocalDateTime.now())
                .build());
    }

    /**
     * 查询当前用户的评价列表
     */
    public List<Comment> myComments() {
        return commentMapper.listByUserId(BaseContext.getCurrentId());
    }

    /**
     * 管理端查询全部评价
     */
    public List<Comment> listAll() {
        return commentMapper.listAll();
    }
}
