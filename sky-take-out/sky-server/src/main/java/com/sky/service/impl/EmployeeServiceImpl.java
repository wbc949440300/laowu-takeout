package com.sky.service.impl;

import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.sky.constant.MessageConstant;
import com.sky.constant.PasswordConstant;
import com.sky.constant.StatusConstant;
import com.sky.context.BaseContext;
import com.sky.dto.EmployeeDTO;
import com.sky.dto.EmployeeLoginDTO;
import com.sky.dto.EmployeePageQueryDTO;
import com.sky.dto.PasswordEditDTO;
import com.sky.entity.Employee;
import com.sky.exception.AccountLockedException;
import com.sky.exception.AccountNotFoundException;
import com.sky.exception.PasswordErrorException;
import com.sky.mapper.EmployeeMapper;
import com.sky.result.PageResult;
import com.sky.service.EmployeeService;
import com.sky.utils.PasswordUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.DigestUtils;

import java.time.LocalDateTime;
import java.util.List;

@Service
@Slf4j
public class EmployeeServiceImpl implements EmployeeService {

    @Autowired
    private EmployeeMapper employeeMapper;

    /**
     * 员工登录
     *
     * @param employeeLoginDTO
     * @return
     */
    public Employee login(EmployeeLoginDTO employeeLoginDTO) {
        String username = employeeLoginDTO.getUsername();
        String password = employeeLoginDTO.getPassword();

        //1、根据用户名查询数据库中的数据
        Employee employee = employeeMapper.getByUsername(username);

        //2、处理各种异常情况（用户名不存在、密码不对、账号被锁定）
        if (employee == null) {
            //账号不存在
            throw new AccountNotFoundException(MessageConstant.ACCOUNT_NOT_FOUND);
        }

        //密码比对：兼容存量 MD5 哈希，新密码一律 BCrypt 加盐
        String storedPassword = employee.getPassword();
        boolean matched;
        if (PasswordUtil.isLegacyMd5(storedPassword)) {
            //旧数据：按 MD5 校验，校验通过后透明升级为 BCrypt（登录即迁移，无需停机改数）
            matched = DigestUtils.md5DigestAsHex(password.getBytes()).equals(storedPassword);
            if (matched) {
                Employee upgrade = new Employee();
                upgrade.setId(employee.getId());
                upgrade.setPassword(PasswordUtil.encode(password));
                employeeMapper.update(upgrade);
                log.info("员工[{}]密码哈希已从 MD5 透明升级为 BCrypt", username);
            }
        } else {
            //BCrypt 哈希：盐内置于哈希结果，直接比对
            matched = PasswordUtil.matches(password, storedPassword);
        }
        if (!matched) {
            //密码错误
            throw new PasswordErrorException(MessageConstant.PASSWORD_ERROR);
        }

        if (employee.getStatus() == StatusConstant.DISABLE) {
            //账号被锁定
            throw new AccountLockedException(MessageConstant.ACCOUNT_LOCKED);
        }

        //3、返回实体对象
        return employee;
    }

    /**
     * 新增员工
     *
     * @param employeeDTO
     */
    public void save(EmployeeDTO employeeDTO) {
        Employee employee = new Employee();

        //对象属性拷贝
        BeanUtils.copyProperties(employeeDTO, employee);

        //设置账号的状态，默认正常状态 1表示正常 0表示锁定
        employee.setStatus(StatusConstant.ENABLE);

        //设置密码，默认密码123456（BCrypt 加盐哈希）
        employee.setPassword(PasswordUtil.encode(PasswordConstant.DEFAULT_PASSWORD));

        //设置当前记录的创建时间和修改时间
        //employee.setCreateTime(LocalDateTime.now());
        //employee.setUpdateTime(LocalDateTime.now());

        //设置当前记录创建人id和修改人id
        //employee.setCreateUser(BaseContext.getCurrentId());
        //employee.setUpdateUser(BaseContext.getCurrentId());

        employeeMapper.insert(employee);
    }

    /**
     * 分页查询
     *
     * @param employeePageQueryDTO
     * @return
     */
    public PageResult pageQuery(EmployeePageQueryDTO employeePageQueryDTO) {
        // select * from employee limit 0,10
        //开始分页查询
        PageHelper.startPage(employeePageQueryDTO.getPage(), employeePageQueryDTO.getPageSize());

        Page<Employee> page = employeeMapper.pageQuery(employeePageQueryDTO);

        long total = page.getTotal();
        List<Employee> records = page.getResult();

        return new PageResult(total, records);
    }

    /**
     * 启用禁用员工账号
     *
     * @param status
     * @param id
     */
    public void startOrStop(Integer status, Long id) {
        // update employee set status = ? where id = ?

        /*Employee employee = new Employee();
        employee.setStatus(status);
        employee.setId(id);*/

        Employee employee = Employee.builder()
                .status(status)
                .id(id)
                .build();

        employeeMapper.update(employee);
    }

    /**
     * 根据id查询员工
     *
     * @param id
     * @return
     */
    public Employee getById(Long id) {
        Employee employee = employeeMapper.getById(id);
        employee.setPassword("****");
        return employee;
    }

    /**
     * 编辑员工信息
     *
     * @param employeeDTO
     */
    public void update(EmployeeDTO employeeDTO) {
        Employee employee = new Employee();
        BeanUtils.copyProperties(employeeDTO, employee);

        //employee.setUpdateTime(LocalDateTime.now());
        //employee.setUpdateUser(BaseContext.getCurrentId());

        employeeMapper.update(employee);
    }

    /**
     * 修改密码：校验旧密码后将新密码 BCrypt 加盐存储。
     * 以当前登录者身份改密（不信任前端传入的 empId，防止越权改他人密码）。
     *
     * @param passwordEditDTO
     */
    public void editPassword(PasswordEditDTO passwordEditDTO) {
        //1、取当前登录员工，不信任前端传的 empId（防越权）
        Long empId = BaseContext.getCurrentId();
        Employee employee = employeeMapper.getById(empId);
        if (employee == null) {
            throw new AccountNotFoundException(MessageConstant.ACCOUNT_NOT_FOUND);
        }

        //2、校验旧密码：兼容存量 MD5 与新 BCrypt 哈希
        String oldPassword = passwordEditDTO.getOldPassword();
        String storedPassword = employee.getPassword();
        boolean matched;
        if (PasswordUtil.isLegacyMd5(storedPassword)) {
            matched = DigestUtils.md5DigestAsHex(oldPassword.getBytes()).equals(storedPassword);
        } else {
            matched = PasswordUtil.matches(oldPassword, storedPassword);
        }
        if (!matched) {
            throw new PasswordErrorException(MessageConstant.PASSWORD_ERROR);
        }

        //3、新密码 BCrypt 加盐后更新（仅更新密码字段）
        Employee update = new Employee();
        update.setId(employee.getId());
        update.setPassword(PasswordUtil.encode(passwordEditDTO.getNewPassword()));
        employeeMapper.update(update);
        log.info("员工[{}]密码修改成功", empId);
    }
}
