package com.sky.controller.admin;

import com.sky.dto.DeliveryStaffDTO;
import com.sky.entity.DeliveryStaff;
import com.sky.result.Result;
import com.sky.service.DeliveryStaffService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/admin/delivery")
@Api(tags = "配送员管理接口")
@Slf4j
public class DeliveryStaffController {

    @Autowired
    private DeliveryStaffService deliveryStaffService;

    /**
     * 新增配送员
     */
    @PostMapping("/staff")
    @ApiOperation("新增配送员")
    public Result save(@RequestBody DeliveryStaffDTO deliveryStaffDTO) {
        log.info("新增配送员：{}", deliveryStaffDTO);
        deliveryStaffService.save(deliveryStaffDTO);
        return Result.success();
    }

    /**
     * 配送员列表
     */
    @GetMapping("/staff/list")
    @ApiOperation("配送员列表")
    public Result<List<DeliveryStaff>> list() {
        return Result.success(deliveryStaffService.listAll());
    }

    /**
     * 启用/停用配送员
     * @param status 0停用 1在职
     */
    @PostMapping("/staff/status/{status}")
    @ApiOperation("启用停用配送员")
    public Result startOrStop(@PathVariable("status") Integer status, Long id) {
        log.info("启用停用配送员：{},{}", status, id);
        deliveryStaffService.startOrStop(status, id);
        return Result.success();
    }
}
