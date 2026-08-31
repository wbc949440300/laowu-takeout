package com.sky.mapper;

import com.sky.entity.DeliveryStaff;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface DeliveryStaffMapper {

    /**
     * 新增配送员
     */
    @Insert("insert into delivery_staff (name, phone, status, create_time) " +
            "values (#{name}, #{phone}, #{status}, #{createTime})")
    void insert(DeliveryStaff deliveryStaff);

    /**
     * 根据id查询
     */
    @Select("select * from delivery_staff where id = #{id}")
    DeliveryStaff getById(Long id);

    /**
     * 查询全部配送员
     */
    @Select("select * from delivery_staff order by create_time desc")
    List<DeliveryStaff> listAll();

    /**
     * 更新状态（在职/停用）
     */
    @Update("update delivery_staff set status = #{status} where id = #{id}")
    void updateStatus(Long id, Integer status);
}
