package com.sky.controller.admin;

import com.sky.entity.Comment;
import com.sky.result.Result;
import com.sky.service.CommentService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController("adminCommentController")
@RequestMapping("/admin/comment")
@Api(tags = "评价管理接口")
@Slf4j
public class CommentController {

    @Autowired
    private CommentService commentService;

    /**
     * 查询全部评价（按时间倒序）
     */
    @GetMapping("/list")
    @ApiOperation("评价列表")
    public Result<List<Comment>> list() {
        return Result.success(commentService.listAll());
    }
}
