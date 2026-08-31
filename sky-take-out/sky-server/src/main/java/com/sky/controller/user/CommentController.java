package com.sky.controller.user;

import com.sky.dto.CommentDTO;
import com.sky.entity.Comment;
import com.sky.result.Result;
import com.sky.service.CommentService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController("userCommentController")
@RequestMapping("/user/comment")
@Api(tags = "用户端评价相关接口")
@Slf4j
public class CommentController {

    @Autowired
    private CommentService commentService;

    /**
     * 提交订单评价（仅限本人已完成订单，一单一评）
     */
    @PostMapping("/{orderId}")
    @ApiOperation("提交订单评价")
    public Result submit(@PathVariable("orderId") Long orderId, @RequestBody CommentDTO commentDTO) {
        log.info("提交评价，订单id：{}，评分：{}", orderId, commentDTO.getRating());
        commentService.submit(orderId, commentDTO);
        return Result.success();
    }

    /**
     * 查询我的评价列表
     */
    @GetMapping("/mine")
    @ApiOperation("查询我的评价")
    public Result<List<Comment>> mine() {
        return Result.success(commentService.myComments());
    }
}
