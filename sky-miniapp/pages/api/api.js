import {request} from "../../utils/request.js"

// 开桌
export const openTable = (params) => 
	request({
		url: `/table/open/${params.tableId}/${params.seatNumber}`,
		method: 'GET',
		params
	})

// 获取桌台状态
export const getTableState = (params) => 
	request({
		url: `/table/tableStatus/${params.shopId}/${params.storeId}/${params.tableId}`,
		method: 'GET',
		params,
	})
	
// 获取购物车菜品
export const getTableOrderDishList = (params) =>
	request({
		url: `/order/shopCart//${params.tableId}`,
		method: 'GET',
		params
	})
// 获取菜品规格
export const getMoreNorm = (params) => 
	request({
		url: `/dish/flavor/${params.dishId}`,
		method: 'GET',
		params,
	})	
	
// 获取菜品分类列表	
export const getList = (params) => 
	request({
		url: `/dish/category`,
		method: 'GET',
		params,
	})
	
// 获取菜品详情	
export const getDishDetail = (params) => 
	request({
		url: `/dish/setmealDishList/${params.setmealId}`,
		method: 'GET',
		params,
	})
	
// 根据分类获取菜品列表
export const getDishList = (params) => 
	request({
		url: `/dish/dishPageList/${params.categoryId}/${params.type}/${params.page}/${params.pageSize}`,
		method: 'GET',
		params
	})

// 加菜
export const addDish = (params) => 
	request({
		url: `/order/addDish`,
		method: 'POST',
		params
	})
	
// 减菜
export const delDish = (params) => 
	request({
		url: `/order/decreaseDish/${params.tableId}/${params.dishId}`,
		method: 'GET',
		params
	})	
	
// 清空购物车
export const clearOrder = (params) => 
	request({
		url: `/order/cleanShopCart/${params.tableId}`,
		method: 'GET',
		params
	})
	
// 提交订单 
export const payOrder = (params) =>
	request({
		url: '/order/payment',
		method: 'PUT',
		params
	})
	
	
// 用户登录
export const userLogin = (params) => {
	return request({
		url: '/user/login',
		method: 'POST',
		params
	})
}

// 菜品和套餐的分类
export const getCategoryList = (params) => {
	return request({
		url: '/category/list',
		method: 'GET',
		params
	})
}

// 查询菜品管理列表
export const dishListByCategoryId = (params) => {
	return request({
		url: '/dish/list',
		method: 'GET',
		params
	})
}

// 文件下载---预览
export const commonDownload = (params) => {
	return request({
		url: '/common/download',
		method: 'GET',
		params
	})
}


// 购物车----加菜功能实现
export const addShoppingCart = (params) => {
	return request({
		url: '/shoppingCart',
		method: 'POST',
		params
	})
}

// 根据type类型查询是套餐的接口
export const querySetmeaList = (params) => {
	return request({
		url: '/setmeal/list',
		method: 'GET',
		params
	})
}

// 获取购物车集合
export const getShoppingCartList = (params) => {
	return request({
		url: '/shoppingCart/list',
		method: 'GET',
		params
	})
}


// 修改购物车数量功能
export const editHoppingCart = (params) => {
	return request({
		url: '/shoppingCart',
		method: 'PUT',
		params
	})
}


// 购物车新增接口-new
export const newAddShoppingCartAdd = (params) => {
	return request({
		url: '/shoppingCart/add',
		method: 'POST',
		params
	})
}


// 购物车减菜接口-new 
export const newShoppingCartSub = (params) => {
	return request({
		url: '/shoppingCart/sub',
		method: 'POST',
		params
	})
}


// 清除购物车
export const delShoppingCart = (params) => {
	return request({
		url: '/shoppingCart/clean',
		method: 'DELETE',
		params
	})
}


// 最近订单和历史订单
export const queryOrderUserPage = (params) => {
	return request({
		url: '/order/historyOrders',
		method: 'GET',
		params
	})
}


// 用户下单
export const submitOrderSubmit = (params) => {
	return request({
		url: '/order/submit',
		method: 'POST',
		params
	})
}


// 查询地址列表
export const queryAddressBookList = (params) => {
	return request({
		url: '/addressBook/list',
		method: 'GET',
		params
	})
}

// 新增默认接口
export const putAddressBookDefault = (params) => {
	return request({
		url: '/addressBook/default',
		method: 'PUT',
		params
	})
}


// 新增地址接口
export const addAddressBook = (params) => {
	return request({
		url: '/addressBook',
		method: 'POST',
		params
	})
}

// 修改地址接口
export const editAddressBook = (params) => {
	return request({
		url: '/addressBook',
		method: 'PUT',
		params
	})
}

// 删除地址
export const delAddressBook = (ids) => {
	console.log(ids)
	return request({
		url: `/addressBook/?ids=${ids}`,
		method: 'DELETE',
		params: { ids }
	})
}

// 查询地址通过id
export const queryAddressBookById = (params) => {
	return request({
		url: `/addressBook/${params.id}`,
		method: 'GET',
		params
	})
}


// 再来一单
export const oneOrderAgain = (params) => {
	return request({
		url: `/order/repetition/${params.id}`,
		method: 'POST',
		params
	})
}

// 查询默认地址
export const getAddressBookDefault = () => {
	return request({
		url: '/addressBook/default',
		method: 'GET'
	})
}


// 此接口为首页查询套餐详情展示的接口
export const querySetmealDishById = (params) => {
	return request({
		url: `/setmeal/dish/${params.id}`,
		method: 'GET'
	})
}

// 订单详情（用户端）
export const getOrderDetail = (params) => {
	return request({
		url: `/order/orderDetail/${params.id}`,
		method: 'GET',
		params
	})
}
// 客户催单
export const reminderOrder = (params) => {
	return request({
		url: `/order/reminder/${params.id}`,
		method: 'GET',
		params
	})
}
// 申请退款
export const applyRefundOrder = (id, reason) => {
	return request({
		url: `/order/refund/apply/${id}`,
		method: 'POST',
		params: { reason }
	})
}