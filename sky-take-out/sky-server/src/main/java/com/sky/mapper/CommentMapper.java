package com.sky.mapper;

import com.sky.entity.Comment;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface CommentMapper {

    /**
     * 插入评价
     */
    @Insert("insert into comment (order_id, user_id, rating, content, create_time) " +
            "values (#{orderId}, #{userId}, #{rating}, #{content}, #{createTime})")
    void insert(Comment comment);

    /**
     * 根据订单id查询评价（一单一评）
     */
    @Select("select * from comment where order_id = #{orderId}")
    Comment getByOrderId(Long orderId);

    /**
     * 查询用户的评价列表（按时间倒序）
     */
    @Select("select * from comment where user_id = #{userId} order by create_time desc")
    List<Comment> listByUserId(Long userId);

    /**
     * 管理端查询全部评价（按时间倒序）
     */
    @Select("select * from comment order by create_time desc")
    List<Comment> listAll();
}
