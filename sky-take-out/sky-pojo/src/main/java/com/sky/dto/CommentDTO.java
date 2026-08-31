package com.sky.dto;

import lombok.Data;
import java.io.Serializable;

/**
 * 用户提交评价
 */
@Data
public class CommentDTO implements Serializable {

    //评分 1-5
    private Integer rating;

    //评价内容
    private String content;
}
