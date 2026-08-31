package com.sky.service.impl;

import com.sky.constant.StatusConstant;
import com.sky.dto.DeliveryStaffDTO;
import com.sky.entity.DeliveryStaff;
import com.sky.exception.OrderBusinessException;
import com.sky.mapper.DeliveryStaffMapper;
import com.sky.service.DeliveryStaffService;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class DeliveryStaffServiceImpl implements DeliveryStaffService {

    @Autowired
    private DeliveryStaffMapper deliveryStaffMapper;

    /**
     * 新增配送员
     */
    public void save(DeliveryStaffDTO deliveryStaffDTO) {
        if (deliveryStaffDTO.getName() == null || deliveryStaffDTO.getPhone() == null) {
            throw new OrderBusinessException("配送员姓名与手机号不能为空");
        }
        DeliveryStaff staff = new DeliveryStaff();
        BeanUtils.copyProperties(deliveryStaffDTO, staff);
        staff.setStatus(StatusConstant.ENABLE);
        staff.setCreateTime(LocalDateTime.now());
        deliveryStaffMapper.insert(staff);
    }

    /**
     * 查询全部配送员
     */
    public List<DeliveryStaff> listAll() {
        return deliveryStaffMapper.listAll();
    }

    /**
     * 启用/停用配送员
     */
    public void startOrStop(Integer status, Long id) {
        if (deliveryStaffMapper.getById(id) == null) {
            throw new OrderBusinessException("配送员不存在");
        }
        deliveryStaffMapper.updateStatus(id, status);
    }
}
