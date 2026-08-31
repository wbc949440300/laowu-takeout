package com.sky.service;

import com.sky.dto.DeliveryStaffDTO;
import com.sky.entity.DeliveryStaff;

import java.util.List;

public interface DeliveryStaffService {

    /**
     * 新增配送员
     * @param deliveryStaffDTO
     */
    void save(DeliveryStaffDTO deliveryStaffDTO);

    /**
     * 查询全部配送员
     */
    List<DeliveryStaff> listAll();

    /**
     * 启用/停用配送员
     * @param status 0停用 1在职
     * @param id
     */
    void startOrStop(Integer status, Long id);
}
