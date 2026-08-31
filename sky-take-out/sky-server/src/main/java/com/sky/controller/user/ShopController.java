package com.sky.controller.user;

import com.sky.result.Result;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.bind.annotation.*;

import java.time.LocalTime;

@RestController("userShopController")
@RequestMapping("/user/shop")
@Api(tags = "店铺相关接口")
@Slf4j
public class ShopController {

    public static final String KEY = "SHOP_STATUS";

    @Autowired
    private RedisTemplate redisTemplate;

    //营业时段配置（24小时制，支持跨天时段如 20:00-02:00）
    @Value("${sky.shop.open-time:08:00}")
    private String openTime;
    @Value("${sky.shop.close-time:22:00}")
    private String closeTime;

    /**
     * 获取店铺的营业状态（开关为开且处于营业时段才算营业中）
     * @return
     */
    @GetMapping("/status")
    @ApiOperation("获取店铺的营业状态")
    public Result<Integer> getStatus(){
        Integer status = (Integer) redisTemplate.opsForValue().get(KEY);
        //开关未开直接打烊；开关已开还要看是否在营业时段内（客服/小程序可据此解释"为什么没开门"）
        int effective = Integer.valueOf(1).equals(status) && inBusinessHours() ? 1 : 0;
        log.info("获取到店铺的营业状态为：{}（开关：{}，营业时段：{}-{}）", effective == 1 ? "营业中" : "打烊中", status, openTime, closeTime);
        return Result.success(effective);
    }

    /**
     * 查询营业时段（客服"几点开门"类问题的数据源）
     */
    @GetMapping("/hours")
    @ApiOperation("查询营业时段")
    public Result<String> getHours(){
        return Result.success(openTime + "-" + closeTime);
    }

    /**
     * 判断当前时间是否在营业时段内（支持跨天时段）
     */
    private boolean inBusinessHours() {
        try {
            LocalTime now = LocalTime.now();
            LocalTime open = LocalTime.parse(openTime);
            LocalTime close = LocalTime.parse(closeTime);
            if (!open.isAfter(close)) {
                //常规时段，如 08:00-22:00
                return !now.isBefore(open) && !now.isAfter(close);
            } else {
                //跨天时段，如 20:00-02:00
                return !now.isBefore(open) || !now.isAfter(close);
            }
        } catch (Exception e) {
            //配置异常时不拦截营业，仅记录日志
            log.error("营业时段配置解析失败：{}-{}", openTime, closeTime, e);
            return true;
        }
    }
}
