package com.sky.service.impl;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.sky.constant.MessageConstant;
import com.sky.constant.StatusConstant;
import com.sky.context.BaseContext;
import com.sky.dto.*;
import com.sky.entity.*;
import com.sky.exception.AddressBookBusinessException;
import com.sky.exception.OrderBusinessException;
import com.sky.exception.ShoppingCartBusinessException;
import com.sky.mapper.*;
import com.sky.result.PageResult;
import com.sky.service.OrderService;
import com.sky.service.OrderStateMachine;
import com.sky.properties.AlipayProperties;
import com.sky.properties.WeChatProperties;
import com.sky.utils.AlipayUtil;
import com.sky.utils.HttpClientUtil;
import com.sky.utils.WeChatPayUtil;
import com.sky.vo.OrderPaymentVO;
import com.sky.vo.OrderStatisticsVO;
import com.sky.vo.OrderSubmitVO;
import com.sky.vo.OrderVO;
import com.sky.websocket.WebSocketServer;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.RandomStringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
@Slf4j
public class OrderServiceImpl implements OrderService {

    @Autowired
    private OrderMapper orderMapper;
    @Autowired
    private OrderDetailMapper orderDetailMapper;
    @Autowired
    private AddressBookMapper addressBookMapper;
    @Autowired
    private ShoppingCartMapper shoppingCartMapper;
    @Autowired
    private UserMapper userMapper;
    @Autowired
    private WeChatPayUtil weChatPayUtil;
    @Autowired
    private WebSocketServer webSocketServer;
    @Autowired
    private RedisTemplate redisTemplate;
    @Autowired
    private DishMapper dishMapper;
    @Autowired
    private SetmealMapper setmealMapper;
    @Autowired
    private OrderTimelineMapper orderTimelineMapper;
    @Autowired
    private ReminderRecordMapper reminderRecordMapper;
    @Autowired
    private RefundApplyMapper refundApplyMapper;
    @Autowired
    private CouponMapper couponMapper;
    @Autowired
    private UserCouponMapper userCouponMapper;
    @Autowired
    private DeliveryStaffMapper deliveryStaffMapper;
    @Autowired
    private AlipayUtil alipayUtil;
    @Autowired
    private AlipayProperties alipayProperties;

    /**
     * 用户下单
     * @param ordersSubmitDTO
     * @return
     */
    @Transactional
    public OrderSubmitVO submitOrder(OrdersSubmitDTO ordersSubmitDTO) {

        //1. 处理各种业务异常（地址簿为空、购物车数据为空）
        AddressBook addressBook = addressBookMapper.getById(ordersSubmitDTO.getAddressBookId());
        if(addressBook == null){
            //抛出业务异常
            throw new AddressBookBusinessException(MessageConstant.ADDRESS_BOOK_IS_NULL);
        }

        //检查用户的收货地址是否超出配送范围（配置开关控制，默认关闭；地图服务异常时不阻断下单）
        if (rangeCheckEnabled) {
            try {
                checkOutOfRange(addressBook.getCityName() + addressBook.getDistrictName() + addressBook.getDetail());
            } catch (OrderBusinessException e) {
                throw e;
            } catch (Exception e) {
                log.warn("配送范围校验失败（地图服务异常，本次跳过）：{}", e.getMessage());
            }
        }

        //查询当前用户的购物车数据
        Long userId = BaseContext.getCurrentId();

        ShoppingCart shoppingCart = new ShoppingCart();
        shoppingCart.setUserId(userId);
        List<ShoppingCart> shoppingCartList = shoppingCartMapper.list(shoppingCart);

        if(shoppingCartList == null || shoppingCartList.size() == 0){
            //抛出业务异常
            throw new ShoppingCartBusinessException(MessageConstant.SHOPPING_CART_IS_NULL);
        }

        //1.5 起售/沽清校验与库存扣减（事务内执行，失败回滚）
        for (ShoppingCart cart : shoppingCartList) {
            if (cart.getDishId() != null) {
                Dish dish = dishMapper.getById(cart.getDishId());
                if (dish == null || !StatusConstant.ENABLE.equals(dish.getStatus())) {
                    throw new OrderBusinessException("菜品【" + cart.getName() + "】已下架，无法下单");
                }
                if (dish.getDailyStock() != null) {
                    //原子扣减，影响行数为0表示库存不足（事务回滚）
                    int affected = dishMapper.decrementStock(cart.getDishId(), cart.getNumber());
                    if (affected == 0) {
                        throw new OrderBusinessException("菜品【" + cart.getName() + "】库存不足");
                    }
                }
            } else if (cart.getSetmealId() != null) {
                Setmeal setmeal = setmealMapper.getById(cart.getSetmealId());
                if (setmeal == null || !StatusConstant.ENABLE.equals(setmeal.getStatus())) {
                    throw new OrderBusinessException("套餐【" + cart.getName() + "】已下架，无法下单");
                }
            }
        }

        //2. 向订单表插入1条数据
        Orders orders = new Orders();
        BeanUtils.copyProperties(ordersSubmitDTO, orders);
        orders.setOrderTime(LocalDateTime.now());
        orders.setPayStatus(Orders.UN_PAID);
        orders.setStatus(Orders.PENDING_PAYMENT);
        orders.setNumber(System.currentTimeMillis() + RandomStringUtils.randomNumeric(4));
        orders.setAddress(addressBook.getDetail());
        orders.setPhone(addressBook.getPhone());
        orders.setConsignee(addressBook.getConsignee());
        orders.setUserId(userId);

        //兼容老版小程序：提交参数可能缺少这些字段（null），补默认值，避免插入 NOT NULL 列报错/空值入库
        if (orders.getPackAmount() == null) {
            orders.setPackAmount(0);
        }
        if (orders.getTablewareNumber() == null) {
            orders.setTablewareNumber(1);
        }
        if (orders.getTablewareStatus() == null) {
            orders.setTablewareStatus(1);
        }
        if (orders.getDeliveryStatus() == null) {
            orders.setDeliveryStatus(1);
        }
        //前端不传金额时，从购物车自动计算总额（不信任前端金额，避免篡改）
        BigDecimal cartTotal = BigDecimal.ZERO;
        for (ShoppingCart cart : shoppingCartList) {
            cartTotal = cartTotal.add(cart.getAmount().multiply(new BigDecimal(cart.getNumber())));
        }
        if (orders.getAmount() == null) {
            orders.setAmount(cartTotal);
        }

        //1.6 优惠券核销：校验归属/可用性/门槛，计算抵扣并更新实付金额（仅限未使用的券）
        Long userCouponId = ordersSubmitDTO.getUserCouponId();
        if (userCouponId != null) {
            UserCoupon userCoupon = userCouponMapper.getById(userCouponId);
            if (userCoupon == null || !userCoupon.getUserId().equals(userId)
                    || !UserCoupon.UNUSED.equals(userCoupon.getStatus())) {
                throw new OrderBusinessException("优惠券不存在或不可用");
            }
            Coupon coupon = couponMapper.getById(userCoupon.getCouponId());
            if (coupon == null || !Integer.valueOf(1).equals(coupon.getStatus())
                    || LocalDateTime.now().isBefore(coupon.getStartTime())
                    || LocalDateTime.now().isAfter(coupon.getEndTime())) {
                throw new OrderBusinessException("优惠券已过期或已下架");
            }
            //门槛校验（基于前端传入的订单总额，与现有金额逻辑保持一致）
            if (orders.getAmount() == null || orders.getAmount().compareTo(coupon.getThresholdAmount()) < 0) {
                throw new OrderBusinessException("订单金额未达到优惠券使用门槛");
            }
            //抵扣计算：满减券直接减；折扣券按比例减；抵扣后至少保留0.01元以便走支付流程
            BigDecimal couponDiscount;
            if (Coupon.TYPE_FULL_REDUCTION.equals(coupon.getType())) {
                couponDiscount = coupon.getDiscountAmount();
            } else {
                couponDiscount = orders.getAmount()
                        .multiply(BigDecimal.ONE.subtract(coupon.getDiscountRate()))
                        .setScale(2, BigDecimal.ROUND_HALF_UP);
            }
            if (couponDiscount.compareTo(orders.getAmount()) >= 0) {
                couponDiscount = orders.getAmount().subtract(new BigDecimal("0.01"));
            }
            orders.setAmount(orders.getAmount().subtract(couponDiscount));
            orders.setCouponId(userCouponId);
            orders.setCouponDiscount(couponDiscount);
        }

        orderMapper.insert(orders);

        //优惠券核销落库（原子更新：并发使用时受影响行数为0，抛异常回滚整个下单事务）
        if (userCouponId != null) {
            int affected = userCouponMapper.markUsed(userCouponId, orders.getId(), LocalDateTime.now());
            if (affected == 0) {
                throw new OrderBusinessException("优惠券已被使用");
            }
        }

        //记录订单时间线：下单
        addTimeline(orders.getId(), OrderTimeline.PLACED, null);

        List<OrderDetail> orderDetailList = new ArrayList<>();
        //3. 向订单明细表插入n条数据
        for (ShoppingCart cart : shoppingCartList) {
            OrderDetail orderDetail = new OrderDetail();//订单明细
            BeanUtils.copyProperties(cart, orderDetail);
            orderDetail.setOrderId(orders.getId());//设置当前订单明细关联的订单id
            orderDetailList.add(orderDetail);
        }

        orderDetailMapper.insertBatch(orderDetailList);

        //4. 清空当前用户的购物车数据
        shoppingCartMapper.deleteByUserId(userId);

        //5. 封装VO返回结果
        OrderSubmitVO orderSubmitVO = OrderSubmitVO.builder()
                .id(orders.getId())
                .orderTime(orders.getOrderTime())
                .orderNumber(orders.getNumber())
                .orderAmount(orders.getAmount())
                .build();

        return orderSubmitVO;
    }

    @Value("${sky.shop.address}")
    private String shopAddress;

    //开发环境 mock 支付开关：跳过微信通道直接标记已支付（仅联调用，生产必须设为 false）
    @Value("${sky.wechat.mock-payment:false}")
    private boolean mockPaymentEnabled;

    //配送范围校验开关（需配置有效百度地图ak后开启）
    @Value("${sky.shop.range-check.enabled:false}")
    private boolean rangeCheckEnabled;

    @Value("${sky.baidu.ak}")
    private String ak;

    /**
     * 检查客户的收货地址是否超出配送范围
     * @param address
     */
    private void checkOutOfRange(String address) {
        Map map = new HashMap();
        map.put("address",shopAddress);
        map.put("output","json");
        map.put("ak",ak);

        //获取店铺的经纬度坐标
        String shopCoordinate = HttpClientUtil.doGet("https://api.map.baidu.com/geocoding/v3", map);

        JSONObject jsonObject = JSON.parseObject(shopCoordinate);
        if(!jsonObject.getString("status").equals("0")){
            throw new OrderBusinessException("店铺地址解析失败");
        }

        //数据解析
        JSONObject location = jsonObject.getJSONObject("result").getJSONObject("location");
        String lat = location.getString("lat");
        String lng = location.getString("lng");
        //店铺经纬度坐标
        String shopLngLat = lat + "," + lng;

        map.put("address",address);
        //获取用户收货地址的经纬度坐标
        String userCoordinate = HttpClientUtil.doGet("https://api.map.baidu.com/geocoding/v3", map);

        jsonObject = JSON.parseObject(userCoordinate);
        if(!jsonObject.getString("status").equals("0")){
            throw new OrderBusinessException("收货地址解析失败");
        }

        //数据解析
        location = jsonObject.getJSONObject("result").getJSONObject("location");
        lat = location.getString("lat");
        lng = location.getString("lng");
        //用户收货地址经纬度坐标
        String userLngLat = lat + "," + lng;

        map.put("origin",shopLngLat);
        map.put("destination",userLngLat);
        map.put("steps_info","0");

        //路线规划
        String json = HttpClientUtil.doGet("https://api.map.baidu.com/directionlite/v1/driving", map);

        jsonObject = JSON.parseObject(json);
        if(!jsonObject.getString("status").equals("0")){
            throw new OrderBusinessException("配送路线规划失败");
        }

        //数据解析
        JSONObject result = jsonObject.getJSONObject("result");
        JSONArray jsonArray = (JSONArray) result.get("routes");
        Integer distance = (Integer) ((JSONObject) jsonArray.get(0)).get("distance");

        if(distance > 5000){
            //配送距离超过5000米
            throw new OrderBusinessException("超出配送范围");
        }
    }

    /**
     * 按原支付通道发起退款：微信（payMethod=1，退款结果靠回调确认）/支付宝（payMethod=2，同步返回结果）
     */
    private String refundByChannel(Orders ordersDB) throws Exception {
        // 开发环境 mock 退款：开启 mock 支付时跳过真实退款通道，直接视为退款成功（仅联调，生产必须关闭）
        if (mockPaymentEnabled) {
            log.info("【mock退款】订单 {} 直接标记退款成功，金额 {}", ordersDB.getNumber(), ordersDB.getAmount());
            return "mock_refunded";
        }
        if (Integer.valueOf(2).equals(ordersDB.getPayMethod())) {
            return alipayUtil.refund(ordersDB.getNumber(), ordersDB.getNumber(), ordersDB.getAmount());
        }
        return weChatPayUtil.refund(
                ordersDB.getNumber(), //商户订单号
                ordersDB.getNumber(), //商户退款单号
                ordersDB.getAmount(), //退款金额，单位 元（订单实付金额）
                ordersDB.getAmount());//原订单金额
    }

    /**
     * 订单支付
     *
     * @param ordersPaymentDTO
     * @return
     */
    public OrderPaymentVO payment(OrdersPaymentDTO ordersPaymentDTO) throws Exception {
        // 当前登录用户id
        Long userId = BaseContext.getCurrentId();
        User user = userMapper.getById(userId);

        // 校验订单存在且属于当前用户，并按订单实付金额发起支付（避免硬编码金额）
        Orders ordersDB = orderMapper.getByNumberAndUserId(ordersPaymentDTO.getOrderNumber(), userId);
        if (ordersDB == null) {
            throw new OrderBusinessException(MessageConstant.ORDER_NOT_FOUND);
        }

        // 开发环境 mock 支付：跳过支付通道直接标记订单已支付（仅联调用，生产必须关闭）
        if (mockPaymentEnabled) {
            paySuccess(ordersPaymentDTO.getOrderNumber());
            log.info("【mock支付】订单 {} 已直接标记为已支付", ordersPaymentDTO.getOrderNumber());
            return OrderPaymentVO.builder().packageStr("mock_paid").build();
        }

        //支付方式分流：2=支付宝（电脑网站支付，返回表单HTML），1=微信小程序支付（默认）
        if (Integer.valueOf(2).equals(ordersPaymentDTO.getPayMethod())) {
            if (!alipayProperties.isConfigured()) {
                throw new OrderBusinessException("支付宝支付暂未开通，请先在 application.yml 完成 sky.alipay 配置");
            }
            String form = alipayUtil.pagePay(
                    ordersPaymentDTO.getOrderNumber(),
                    ordersDB.getAmount(),
                    "老吴外卖订单");
            return OrderPaymentVO.builder().alipayForm(form).build();
        }

        //调用微信支付接口，生成预支付交易单
        JSONObject jsonObject = weChatPayUtil.pay(
                ordersPaymentDTO.getOrderNumber(), //商户订单号
                ordersDB.getAmount(), //支付金额，单位 元（订单实付金额）
                "老吴外卖订单", //商品描述
                user.getOpenid() //微信用户的openid
        );

        if (jsonObject.getString("code") != null && jsonObject.getString("code").equals("ORDERPAID")) {
            throw new OrderBusinessException("该订单已支付");
        }

        OrderPaymentVO vo = jsonObject.toJavaObject(OrderPaymentVO.class);
        vo.setPackageStr(jsonObject.getString("package"));

        return vo;
    }

    /**
     * 支付成功，修改订单状态
     *
     * @param outTradeNo
     */
    public void paySuccess(String outTradeNo) {
        // 支付回调来自微信服务器，无登录态（BaseContext 为空），只能按订单号查询（订单号全局唯一）
        Orders ordersDB = orderMapper.getByNumber(outTradeNo);

        if (ordersDB == null) {
            log.warn("支付回调订单不存在，商户订单号：{}", outTradeNo);
            return;
        }

        // 幂等：已支付订单忽略重复回调，避免重复来单提醒
        if (Orders.PAID.equals(ordersDB.getPayStatus())) {
            log.info("订单已支付，忽略重复回调，商户订单号：{}", outTradeNo);
            return;
        }

        // 根据订单id更新订单的状态、支付方式、支付状态、结账时间
        Orders orders = Orders.builder()
                .id(ordersDB.getId())
                .status(Orders.TO_BE_CONFIRMED)
                .payStatus(Orders.PAID)
                .checkoutTime(LocalDateTime.now())
                .build();

        orderMapper.update(orders);

        //记录订单时间线：支付成功
        addTimeline(ordersDB.getId(), OrderTimeline.PAID, null);

        //通过websocket向客户端浏览器推送消息 type orderId content
        Map map = new HashMap();
        map.put("type",1); // 1表示来单提醒 2表示客户催单
        map.put("orderId",ordersDB.getId());
        map.put("content","订单号：" + outTradeNo);

        String json = JSON.toJSONString(map);
        webSocketServer.sendToAllClient(json);
    }

    /**
     * 退款成功回调处理，确认退款状态（幂等：重复回调不重复处理）
     * @param outTradeNo 商户订单号
     * @param refundStatus 退款状态（SUCCESS/CHANGE/ABNORMAL）
     */
    public void refundSuccess(String outTradeNo, String refundStatus) {
        // 回调无登录态，按订单号查询（订单号全局唯一）
        Orders ordersDB = orderMapper.getByNumber(outTradeNo);
        if (ordersDB == null) {
            log.warn("退款回调订单不存在，商户订单号：{}", outTradeNo);
            return;
        }

        if (!"SUCCESS".equals(refundStatus)) {
            // 退款异常/变更，仅记录日志，人工介入处理；同时把支付状态回退为已支付，避免误标退款
            log.warn("退款未成功，商户订单号：{}，退款状态：{}", outTradeNo, refundStatus);
            Orders orders = Orders.builder()
                    .id(ordersDB.getId())
                    .payStatus(Orders.PAID)
                    .build();
            orderMapper.update(orders);
            return;
        }

        // 幂等：已是退款状态则不重复更新
        if (Orders.REFUND.equals(ordersDB.getPayStatus())) {
            log.info("订单已处于退款状态，忽略重复回调，商户订单号：{}", outTradeNo);
            return;
        }

        // 确认退款：更新支付状态为退款，并通知管理端（复用现有WebSocket广播）
        Orders orders = Orders.builder()
                .id(ordersDB.getId())
                .payStatus(Orders.REFUND)
                .build();
        orderMapper.update(orders);

        Map map = new HashMap();
        map.put("type", 3); // 3表示退款提醒（前端暂无对应处理，仅预留）
        map.put("orderId", ordersDB.getId());
        map.put("content", "退款成功，订单号：" + outTradeNo);
        webSocketServer.sendToAllClient(JSON.toJSONString(map));
    }

    /**
     * 用户端订单分页查询
     *
     * @param pageNum
     * @param pageSize
     * @param status
     * @return
     */
    public PageResult pageQuery4User(int pageNum, int pageSize, Integer status) {
        // 设置分页
        PageHelper.startPage(pageNum, pageSize);

        OrdersPageQueryDTO ordersPageQueryDTO = new OrdersPageQueryDTO();
        ordersPageQueryDTO.setUserId(BaseContext.getCurrentId());
        ordersPageQueryDTO.setStatus(status);

        // 分页条件查询
        Page<Orders> page = orderMapper.pageQuery(ordersPageQueryDTO);

        List<OrderVO> list = new ArrayList();

        // 查询出订单明细，并封装入OrderVO进行响应（批量查询消除 N+1）
        if (page != null && page.getTotal() > 0) {
            List<Long> orderIds = new ArrayList<>();
            for (Orders orders : page) {
                orderIds.add(orders.getId());
            }
            // 一次查出全部明细，按订单id分组后内存匹配，替代逐单查库
            Map<Long, List<OrderDetail>> detailMap = orderDetailMapper.getByOrderIds(orderIds).stream()
                    .collect(Collectors.groupingBy(OrderDetail::getOrderId));

            for (Orders orders : page) {
                OrderVO orderVO = new OrderVO();
                BeanUtils.copyProperties(orders, orderVO);
                List<OrderDetail> details = detailMap.getOrDefault(orders.getId(), new ArrayList<>());
                //同时填充两个字段：orderDetails 供小程序历史订单页读取，orderDetailList 保留原有兼容
                orderVO.setOrderDetails(details);
                orderVO.setOrderDetailList(details);
                list.add(orderVO);
            }
        }
        return new PageResult(page.getTotal(), list);
    }

    /**
     * 查询订单详情
     *
     * @param id
     * @return
     */
    public OrderVO details(Long id) {
        // 根据id查询订单
        Orders orders = orderMapper.getById(id);

        // 查询该订单对应的菜品/套餐明细
        List<OrderDetail> orderDetailList = orderDetailMapper.getByOrderId(orders.getId());

        // 将该订单及其详情封装到OrderVO并返回
        OrderVO orderVO = new OrderVO();
        BeanUtils.copyProperties(orders, orderVO);
        orderVO.setOrderDetailList(orderDetailList);

        return orderVO;
    }

    /**
     * 用户端查询订单详情（校验订单归属，防止越权查看他人订单）
     *
     * @param id
     * @return
     */
    public OrderVO details4User(Long id) {
        // 根据id查询订单
        Orders orders = orderMapper.getById(id);

        // 校验订单存在且属于当前登录用户，归属不符按订单不存在处理，避免暴露订单是否存在
        if (orders == null || !orders.getUserId().equals(BaseContext.getCurrentId())) {
            throw new OrderBusinessException(MessageConstant.ORDER_NOT_FOUND);
        }

        return details(id);
    }

    /**
     * 用户取消订单
     *
     * @param id
     */
    @Transactional
    public void userCancelById(Long id) throws Exception {
        // 根据id查询订单
        Orders ordersDB = orderMapper.getById(id);

        // 校验订单存在且属于当前登录用户，避免通过用户接口越权取消订单
        if (ordersDB == null || !ordersDB.getUserId().equals(BaseContext.getCurrentId())) {
            throw new OrderBusinessException(MessageConstant.ORDER_NOT_FOUND);
        }
        //订单状态 1待付款 2待接单 3已接单 4派送中 5已完成 6已取消
        if (ordersDB.getStatus() > 2) {
            throw new OrderBusinessException(MessageConstant.ORDER_STATUS_ERROR);
        }
        acquireOperationLock("cancel", id, 30);

        Orders orders = new Orders();
        orders.setId(ordersDB.getId());

        // 订单处于待接单状态下取消，需要进行退款（按订单实付金额退款，按原支付通道）
        if (ordersDB.getStatus().equals(Orders.TO_BE_CONFIRMED)) {
            refundByChannel(ordersDB);

            //支付状态修改为 退款
            orders.setPayStatus(Orders.REFUND);
        }

        // 更新订单状态、取消原因、取消时间
        orders.setStatus(Orders.CANCELLED);
        orders.setCancelReason("用户取消");
        orders.setCancelTime(LocalDateTime.now());
        orderMapper.update(orders);

        //记录订单时间线：用户取消（已支付订单同时发生退款）
        addTimeline(id, OrderTimeline.CANCELLED,
                ordersDB.getStatus().equals(Orders.TO_BE_CONFIRMED) ? "用户取消，已申请退款" : "用户取消");
    }

    /**
     * 再来一单
     *
     * @param id
     */
    @Transactional
    public void repetition(Long id) {
        // 查询当前用户id，校验订单归属（防越权）
        Long userId = BaseContext.getCurrentId();
        Orders ordersDB = orderMapper.getById(id);
        if (ordersDB == null || !ordersDB.getUserId().equals(userId)) {
            throw new OrderBusinessException(MessageConstant.ORDER_NOT_FOUND);
        }
        acquireOperationLock("repetition", id, 10);

        // 根据订单id查询当前订单详情
        List<OrderDetail> orderDetailList = orderDetailMapper.getByOrderId(id);

        // 将订单详情对象转换为购物车对象，过滤已下架/售罄的商品（不再把失效商品带入购物车）
        boolean skipped = false;
        List<ShoppingCart> shoppingCartList = new ArrayList<>();
        for (OrderDetail detail : orderDetailList) {
            boolean onSale;
            if (detail.getDishId() != null) {
                Dish dish = dishMapper.getById(detail.getDishId());
                onSale = dish != null && StatusConstant.ENABLE.equals(dish.getStatus())
                        && (dish.getDailyStock() == null || dish.getDailyStock() > 0);
            } else if (detail.getSetmealId() != null) {
                Setmeal setmeal = setmealMapper.getById(detail.getSetmealId());
                onSale = setmeal != null && StatusConstant.ENABLE.equals(setmeal.getStatus());
            } else {
                onSale = false;
            }
            if (!onSale) {
                skipped = true;
                continue;
            }

            ShoppingCart shoppingCart = new ShoppingCart();
            // 将原订单详情里面的菜品信息重新复制到购物车对象中
            BeanUtils.copyProperties(detail, shoppingCart, "id");
            shoppingCart.setUserId(userId);
            shoppingCart.setCreateTime(LocalDateTime.now());
            shoppingCartList.add(shoppingCart);
        }

        if (shoppingCartList.isEmpty()) {
            throw new OrderBusinessException("该订单商品均已下架或售罄，无法再来一单");
        }

        // 将购物车对象批量添加到数据库；如有商品被过滤，告知用户（前端可据此提示）
        shoppingCartMapper.insertBatch(shoppingCartList);
        if (skipped) {
            throw new OrderBusinessException(MessageConstant.REPETITION_GOODS_OFF_SALE);
        }
    }

    /**
     * 订单搜索
     *
     * @param ordersPageQueryDTO
     * @return
     */
    public PageResult conditionSearch(OrdersPageQueryDTO ordersPageQueryDTO) {
        PageHelper.startPage(ordersPageQueryDTO.getPage(), ordersPageQueryDTO.getPageSize());

        Page<Orders> page = orderMapper.pageQuery(ordersPageQueryDTO);

        // 部分订单状态，需要额外返回订单菜品信息，将Orders转化为OrderVO
        List<OrderVO> orderVOList = getOrderVOList(page);

        return new PageResult(page.getTotal(), orderVOList);
    }

    private List<OrderVO> getOrderVOList(Page<Orders> page) {
        // 需要返回订单菜品信息，自定义OrderVO响应结果（批量查明细消除 N+1）
        List<OrderVO> orderVOList = new ArrayList<>();

        List<Orders> ordersList = page.getResult();
        if (!CollectionUtils.isEmpty(ordersList)) {
            List<Long> orderIds = ordersList.stream().map(Orders::getId).collect(Collectors.toList());
            Map<Long, List<OrderDetail>> detailMap = orderDetailMapper.getByOrderIds(orderIds).stream()
                    .collect(Collectors.groupingBy(OrderDetail::getOrderId));

            for (Orders orders : ordersList) {
                // 将共同字段复制到OrderVO
                OrderVO orderVO = new OrderVO();
                BeanUtils.copyProperties(orders, orderVO);

                // 将每一条订单菜品信息拼接为字符串（格式：宫保鸡丁*3；）
                List<OrderDetail> details = detailMap.getOrDefault(orders.getId(), new ArrayList<>());
                String orderDishes = details.stream()
                        .map(x -> x.getName() + "*" + x.getNumber() + ";")
                        .collect(Collectors.joining());

                // 将订单菜品信息封装到orderVO中，并添加到orderVOList
                orderVO.setOrderDishes(orderDishes);
                orderVOList.add(orderVO);
            }
        }
        return orderVOList;
    }

    /**
     * 各个状态的订单数量统计
     *
     * @return
     */
    public OrderStatisticsVO statistics() {
        // 根据状态，分别查询出待接单、待派送、派送中的订单数量
        Integer toBeConfirmed = orderMapper.countStatus(Orders.TO_BE_CONFIRMED);
        Integer confirmed = orderMapper.countStatus(Orders.CONFIRMED);
        Integer deliveryInProgress = orderMapper.countStatus(Orders.DELIVERY_IN_PROGRESS);

        // 将查询出的数据封装到orderStatisticsVO中响应
        OrderStatisticsVO orderStatisticsVO = new OrderStatisticsVO();
        orderStatisticsVO.setToBeConfirmed(toBeConfirmed);
        orderStatisticsVO.setConfirmed(confirmed);
        orderStatisticsVO.setDeliveryInProgress(deliveryInProgress);
        return orderStatisticsVO;
    }

    /**
     * 接单
     *
     * @param ordersConfirmDTO
     */
    public void confirm(OrdersConfirmDTO ordersConfirmDTO) {
        OrderStateMachine.require(orderMapper.getById(ordersConfirmDTO.getId()), Orders.CONFIRMED);
        Orders orders = Orders.builder()
                .id(ordersConfirmDTO.getId())
                .status(Orders.CONFIRMED)
                .build();

        orderMapper.update(orders);

        //记录订单时间线：接单
        addTimeline(ordersConfirmDTO.getId(), OrderTimeline.CONFIRMED, null);
    }

    /**
     * 拒单
     *
     * @param ordersRejectionDTO
     */
    public void rejection(OrdersRejectionDTO ordersRejectionDTO) throws Exception {
        // 根据id查询订单
        Orders ordersDB = orderMapper.getById(ordersRejectionDTO.getId());

        // 订单只有存在且状态为2（待接单）才可以拒单
        if (ordersDB == null || !ordersDB.getStatus().equals(Orders.TO_BE_CONFIRMED)) {
            throw new OrderBusinessException(MessageConstant.ORDER_STATUS_ERROR);
        }

        //支付状态
        Integer payStatus = ordersDB.getPayStatus();
        if (payStatus == Orders.PAID) {
            //用户已支付，需要退款（按订单实付金额，按原支付通道）
            String refund = refundByChannel(ordersDB);
            log.info("申请退款：{}", refund);
        }

        // 拒单需要退款，根据订单id更新订单状态、拒单原因、取消时间
        Orders orders = new Orders();
        orders.setId(ordersDB.getId());
        orders.setStatus(Orders.CANCELLED);
        orders.setRejectionReason(ordersRejectionDTO.getRejectionReason());
        orders.setCancelTime(LocalDateTime.now());

        orderMapper.update(orders);

        //记录订单时间线：商家拒单
        addTimeline(ordersDB.getId(), OrderTimeline.CANCELLED, "商家拒单：" + ordersRejectionDTO.getRejectionReason());
    }

    /**
     * 取消订单
     *
     * @param ordersCancelDTO
     */
    public void cancel(OrdersCancelDTO ordersCancelDTO) throws Exception {
        // 根据id查询订单
        Orders ordersDB = orderMapper.getById(ordersCancelDTO.getId());
        OrderStateMachine.require(ordersDB, Orders.CANCELLED);

        //支付状态
        Integer payStatus = ordersDB.getPayStatus();
        if (payStatus == 1) {
            //用户已支付，需要退款（按订单实付金额，按原支付通道）
            String refund = refundByChannel(ordersDB);
            log.info("申请退款：{}", refund);
        }

        // 管理端取消订单需要退款，根据订单id更新订单状态、取消原因、取消时间
        Orders orders = new Orders();
        orders.setId(ordersCancelDTO.getId());
        orders.setStatus(Orders.CANCELLED);
        orders.setCancelReason(ordersCancelDTO.getCancelReason());
        orders.setCancelTime(LocalDateTime.now());
        orderMapper.update(orders);

        //记录订单时间线：商家取消
        addTimeline(ordersCancelDTO.getId(), OrderTimeline.CANCELLED, "商家取消：" + ordersCancelDTO.getCancelReason());
    }

    /**
     * 派送订单
     *
     * @param id
     */
    public void delivery(Long id, Long riderId) {
        // 根据id查询订单
        Orders ordersDB = orderMapper.getById(id);

        // 校验订单是否存在，并且状态为3
        OrderStateMachine.require(ordersDB, Orders.DELIVERY_IN_PROGRESS);

        Orders orders = new Orders();
        orders.setId(ordersDB.getId());
        // 更新订单状态,状态转为派送中；可选指派配送员（骑手）
        orders.setStatus(Orders.DELIVERY_IN_PROGRESS);
        if (riderId != null) {
            DeliveryStaff staff = deliveryStaffMapper.getById(riderId);
            if (staff == null || !StatusConstant.ENABLE.equals(staff.getStatus())) {
                throw new OrderBusinessException("配送员不存在或已停用");
            }
            orders.setDeliveryStaffId(staff.getId());
            orders.setDeliveryStaffName(staff.getName());
        }

        orderMapper.update(orders);

        //记录订单时间线：派送
        addTimeline(ordersDB.getId(), OrderTimeline.DELIVERING, null);
    }

    /**
     * 完成订单
     *
     * @param id
     */
    public void complete(Long id) {
        // 根据id查询订单
        Orders ordersDB = orderMapper.getById(id);

        // 校验订单是否存在，并且状态为4
        OrderStateMachine.require(ordersDB, Orders.COMPLETED);

        Orders orders = new Orders();
        orders.setId(ordersDB.getId());
        // 更新订单状态,状态转为完成
        orders.setStatus(Orders.COMPLETED);
        orders.setDeliveryTime(LocalDateTime.now());

        orderMapper.update(orders);

        //记录订单时间线：完成
        addTimeline(ordersDB.getId(), OrderTimeline.COMPLETED, null);
    }

    /**
     * 客户催单
     * @param id
     */
    public void reminder(Long id) {
        // 根据id查询订单
        Orders ordersDB = orderMapper.getById(id);

        // 校验订单存在且属于当前用户（防越权催单，归属不符按订单不存在处理）
        if (ordersDB == null || !ordersDB.getUserId().equals(BaseContext.getCurrentId())) {
            throw new OrderBusinessException(MessageConstant.ORDER_NOT_FOUND);
        }
        // 仅已接单或配送中的订单允许催单，避免对待付款/已完成订单产生无效提醒
        if (ordersDB.getStatus() < Orders.CONFIRMED || ordersDB.getStatus() > Orders.DELIVERY_IN_PROGRESS) {
            throw new OrderBusinessException(MessageConstant.ORDER_STATUS_ERROR);
        }

        // 催单频控：同一订单 60 秒内只允许催一次（Redis 占位锁，后续客服催单工具可复用同一 key）
        String reminderKey = "reminder:order:" + id;
        Boolean locked = (Boolean) redisTemplate.opsForValue().setIfAbsent(reminderKey, "1", 60, TimeUnit.SECONDS);
        if (locked == null || !locked) {
            throw new OrderBusinessException(MessageConstant.REMINDER_TOO_FREQUENT);
        }

        //记录催单记录（供商家/客服追溯）
        reminderRecordMapper.insert(ReminderRecord.builder()
                .orderId(id)
                .userId(BaseContext.getCurrentId())
                .createTime(LocalDateTime.now())
                .build());

        Map map = new HashMap();
        map.put("type",2); //1表示来单提醒 2表示客户催单
        map.put("orderId",id);
        map.put("content","订单号：" + ordersDB.getNumber());

        //通过websocket向客户端浏览器推送消息
        webSocketServer.sendToAllClient(JSON.toJSONString(map));
    }

    /**
     * 记录订单时间线事件（辅助数据，写入失败不影响主流程）
     */
    private void addTimeline(Long orderId, String eventType, String remark) {
        try {
            orderTimelineMapper.insert(OrderTimeline.builder()
                    .orderId(orderId)
                    .eventType(eventType)
                    .remark(remark)
                    .createTime(LocalDateTime.now())
                    .build());
        } catch (Exception e) {
            log.error("订单时间线写入失败：orderId={}, event={}", orderId, eventType, e);
        }
    }

    /**
     * 用户端查询订单时间线（校验归属，防越权）
     */
    public List<OrderTimeline> timeline4User(Long orderId) {
        Orders ordersDB = orderMapper.getById(orderId);
        if (ordersDB == null || !ordersDB.getUserId().equals(BaseContext.getCurrentId())) {
            throw new OrderBusinessException(MessageConstant.ORDER_NOT_FOUND);
        }
        return orderTimelineMapper.getByOrderId(orderId);
    }

    @Override
    public List<OrderTimeline> timeline(Long orderId) {
        if (orderMapper.getById(orderId) == null) {
            throw new OrderBusinessException(MessageConstant.ORDER_NOT_FOUND);
        }
        return orderTimelineMapper.getByOrderId(orderId);
    }

    /**
     * 用户申请退款（已支付且未完成/未取消的订单）
     */
    public void applyRefund(Long orderId, RefundApplyDTO refundApplyDTO) {
        Long userId = BaseContext.getCurrentId();
        Orders ordersDB = orderMapper.getById(orderId);
        //订单归属校验（防越权）
        if (ordersDB == null || !ordersDB.getUserId().equals(userId)) {
            throw new OrderBusinessException(MessageConstant.ORDER_NOT_FOUND);
        }
        //业务规则：已支付且状态为待接单/已接单/派送中的订单可申请退款；已完成/已取消不支持自助退款
        if (!Orders.PAID.equals(ordersDB.getPayStatus()) || ordersDB.getStatus() > Orders.DELIVERY_IN_PROGRESS) {
            throw new OrderBusinessException(MessageConstant.REFUND_STATUS_ERROR);
        }
        //已有待审核申请不允许重复提交（被拒绝后可重新申请）
        RefundApply latest = refundApplyMapper.getLatestByOrderId(orderId);
        if (latest != null && RefundApply.PENDING.equals(latest.getStatus())) {
            throw new OrderBusinessException(MessageConstant.REFUND_APPLY_EXISTS);
        }
        acquireOperationLock("refund-apply", orderId, 30);

        refundApplyMapper.insert(RefundApply.builder()
                .orderId(orderId)
                .userId(userId)
                .reason(refundApplyDTO.getReason())
                .status(RefundApply.PENDING)
                .createTime(LocalDateTime.now())
                .build());
        addTimeline(orderId, OrderTimeline.REFUND_APPLY, refundApplyDTO.getReason());
    }

    /** 防止同一用户对同一订单的副作用操作并发执行。长期幂等由订单状态/申请状态校验负责。 */
    private void acquireOperationLock(String action, Long orderId, int seconds) {
        String key = "op-lock:" + action + ":user:" + BaseContext.getCurrentId() + ":order:" + orderId;
        Boolean acquired = (Boolean) redisTemplate.opsForValue().setIfAbsent(key, "1", seconds, TimeUnit.SECONDS);
        if (acquired == null || !acquired) {
            throw new OrderBusinessException(MessageConstant.REQUEST_TOO_FREQUENT);
        }
    }

    /**
     * 用户查询退款进度（校验归属）
     */
    public RefundApply getRefundProgress(Long orderId) {
        Orders ordersDB = orderMapper.getById(orderId);
        if (ordersDB == null || !ordersDB.getUserId().equals(BaseContext.getCurrentId())) {
            throw new OrderBusinessException(MessageConstant.ORDER_NOT_FOUND);
        }
        return refundApplyMapper.getLatestByOrderId(orderId);
    }

    /**
     * 管理端查询退款申请列表（按状态过滤，可为全部）
     */
    public List<RefundApply> listRefundApply(Integer status) {
        return refundApplyMapper.list(status);
    }

    /**
     * 管理端处理退款申请：同意→按订单实付金额调用退款通道；拒绝→记录原因；幂等保护防重复处理
     */
    public void handleRefund(RefundHandleDTO refundHandleDTO) throws Exception {
        RefundApply apply = refundApplyMapper.getById(refundHandleDTO.getId());
        if (apply == null) {
            throw new OrderBusinessException(MessageConstant.REFUND_APPLY_NOT_FOUND);
        }
        if (!RefundApply.PENDING.equals(apply.getStatus())) {
            throw new OrderBusinessException("退款申请已处理，请勿重复操作");
        }

        if (RefundApply.APPROVED.equals(refundHandleDTO.getStatus())) {
            Orders ordersDB = orderMapper.getById(apply.getOrderId());
            if (ordersDB == null) {
                throw new OrderBusinessException(MessageConstant.ORDER_NOT_FOUND);
            }
            try {
                //按订单实付金额退款，按原支付通道（商户退款单号复用订单号）
                refundByChannel(ordersDB);
            } catch (Exception e) {
                //退款通道异常：申请保持待审核状态，由人工重试，不写错误状态
                log.error("退款通道调用失败：{}", e.getMessage());
                throw new OrderBusinessException(MessageConstant.REFUND_CHANNEL_ERROR);
            }
            //商家同意退款：支付状态改为退款，并将订单标记为已取消（退款后订单作废）
            orderMapper.update(Orders.builder()
                    .id(ordersDB.getId())
                    .payStatus(Orders.REFUND)
                    .status(Orders.CANCELLED)
                    .cancelReason("商家同意退款")
                    .cancelTime(LocalDateTime.now())
                    .build());
            addTimeline(apply.getOrderId(), OrderTimeline.REFUND_APPLY, "商家已同意退款，订单已取消");
        }

        //更新审核结果
        apply.setStatus(refundHandleDTO.getStatus());
        apply.setHandleRemark(refundHandleDTO.getHandleRemark());
        apply.setHandleTime(LocalDateTime.now());
        refundApplyMapper.update(apply);
    }
}
