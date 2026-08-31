package com.sky.test;

import com.sky.constant.JwtClaimsConstant;
import com.sky.utils.JwtUtil;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

/**
 * 本地测试用：生成用户端 JWT（authentication 头）。
 * 用途：在没有小程序/微信登录环境时，用 curl / knife4j 直接调试 /user/** 接口。
 * 注意：userId 必须与数据库 user 表中真实存在的 id 一致。
 */
public class JwtGenTest {

    //与 application.yml 中 sky.jwt.user-secret-key 保持一致
    private static final String USER_SECRET = "itheima";
    //72 小时，足够调试用
    private static final long TTL_MILLIS = 72 * 3600 * 1000L;

    @Test
    public void genUserToken() {
        //改成你要模拟的用户id（数据库 user 表中的 id）
        Long userId = 4L;

        Map<String, Object> claims = new HashMap<>();
        claims.put(JwtClaimsConstant.USER_ID, userId);
        String token = JwtUtil.createJWT(USER_SECRET, TTL_MILLIS, claims);

        System.out.println("userId=" + userId + " 的用户端 token：");
        System.out.println(token);
        System.out.println("请求时放入请求头：authentication: " + token);
    }
}
