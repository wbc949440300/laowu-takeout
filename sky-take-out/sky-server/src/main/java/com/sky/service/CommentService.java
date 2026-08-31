package com.sky.service;

import com.sky.dto.CommentDTO;
import com.sky.entity.Comment;

import java.util.List;

public interface CommentService {

    /**
     * 用户提交订单评价（仅限本人已完成订单，一单一评）
     * @param orderId
     * @param commentDTO
     */
    void submit(Long orderId, CommentDTO commentDTO);

    /**
     * 查询当前用户的评价列表
     */
    List<Comment> myComments();

    /**
     * 管理端查询全部评价
     */
    List<Comment> listAll();
}
