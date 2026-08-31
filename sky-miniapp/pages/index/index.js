import navBar from '../common/Navbar/navbar.vue'
import 
	{ 
		openTable, 
		getTableState, 
		getList, 
		clearOrder, 
		getMoreNorm,
		getDishDetail, 
		getDishList, 
		addDish, 
		delDish, 
		getTableOrderDishList, 
		// 老吴外卖相关的接口
		userLogin, 
		getCategoryList, 
		dishListByCategoryId, 
		commonDownload,
		// 加菜
		addShoppingCart,
		// 查询套餐列表的接口
		querySetmeaList,
		// 获取购物车集合
		getShoppingCartList,
		// 新的购物车添加逻辑接口
		newAddShoppingCartAdd,
		// 新的购物车减少接口
		newShoppingCartSub,
		// 清空购物车
		delShoppingCart,
		// 此接口为首页查询套餐详情展示的接口
		querySetmealDishById
	} from '../api/api.js'
import initWebScoket from '../../utils/webscoket'
import {mapState, mapMutations, mapActions} from 'vuex'
import { baseUrl, agentBaseUrl } from '../../utils/env'
export default {
	data () {
		return {
			showChat: false,
			chatMessages: [{ role: 'bot', content: '您好，我是老吴外卖智能客服，请问有什么可以帮您？' }],
			chatInput: '',
			chatThreadId: '',
			chatLoading: false,
			chatScrollTop: 0,
			title: 'Hello',
			// 去结算部分
			openOrderCartList: false,
			// 存放左侧滚动区域菜品分类数组
			typeListData: [],
			dishListData: [],
			// 存放右侧对应菜品每个菜名称的数组
			dishListItems: [],
			dishDetailes: {},
			openDetailPop: false,
			openMoreNormPop: false,
			moreNormDataes: null,
			tableInfo:null,
			moreNormDishdata:null,
			moreNormdata:null,
			// 套餐中查询到的菜品名称
			dishMealData:null,
			openTablePeoPleNumber: 1,
			orderData: 0,
			// 选中左侧菜品的索引
			typeIndex: 0,
			// 控制菜品详情显示
			openTablePop: false,
			// 规格有关的数组
			flavorDataes: [],
			// 加入购物车数量
			orderDishNumber: 0,
			// 菜品金额
			orderDishPrice: 0,
			params: {
				shopId: 'f3deb',
				storeId: '1282344676983062530',
				tableId: '1282346960773238786'
			 },
			 // 添加一个右侧number更新以后重新刷新接口的id --- 这个id来自左侧菜品分类的id
			 rightIdAndType: {}
		}
	},
	computed: {
		// 购物车信息列表
		orderListDataes: function () {
			return this.orderListData()
			// return this.orderListData().dishList
		},
		loaddingSt: function () {
			return this.lodding()
		},
		orderAndUserInfo: function () {
			let orderData = []
			Array.isArray(this.orderListDataes) && this.orderListDataes.forEach((n,i) => {
				let userData = {}
				userData.nickName = n.name ?? ''
				userData.avatarUrl = n.image ?? ''
				userData.dishList = [n]
				const num = orderData.findIndex(o => o.nickName == userData.nickName)
				if (num != -1) {
					orderData[num].dishList.push(n)
				} else {
					orderData.push(userData)
				}
			})
			return orderData
		},
		ht: function () {
			try {
				return uni.getMenuButtonBoundingClientRect().top + uni.getMenuButtonBoundingClientRect().height + 7
			} catch (e) {
				return 7
			}
		}
	},
	components: { navBar },
	onLoad (options) {
		uni.onNetworkStatusChange(function(res) {
			if (res.isConnected == false) {
				uni.navigateTo({url: '/pages/nonet/index'})
			} 
		})
		if (options) {
			if (!options.status && !options.formOrder && !uni.getStorageSync('sky_token')) {
				this.getData()
			}
		}
		// 有sessionId免授权
		// this.sessionId() && this.init()
	},
	onShow () {
		// 有sessionId免授权
		// 修复：sessionId 是 vuex state 而非方法，原来当函数调用会抛错导致首页不加载数据
		// 改为根据登录令牌判断是否加载（onShow 每次显示都刷新，保证返回首页数据正常）
		if (uni.getStorageSync('sky_token')) {
			this.init()
		}
	},
	watch: {
		chatMessages() { this.$nextTick(() => { this.chatScrollTop = this.chatScrollTop === 99999 ? 100000 : 99999 }) }
	},
	methods: {
		toggleChat() { this.showChat = !this.showChat },
		sendChatMsg() {
				const text = (this.chatInput || '').trim()
				if (!text) return
				if (this.chatLoading) { uni.showToast({ title: 'AI正在思考，请稍候', icon: 'none' }); return }
				this.chatInput = ''
				this.chatMessages.push({ role: 'user', content: text })
				this.doChat({ message: text })
			},
			doChat(payload) {
				if (this.chatThreadId) payload.thread_id = this.chatThreadId
				this.chatLoading = true
				uni.request({
					url: `${agentBaseUrl}/chat`,
				timeout: 90000,
					method: 'POST',
					data: payload,
					header: { 'Content-Type': 'application/json', 'authentication': uni.getStorageSync('sky_token') || '' },
					 success: (res) => {
						this.chatLoading = false
						const data = res.data || {}
						if (res.statusCode === 401 || (data.error && data.error.code === 4010)) {
							this.chatThreadId = ''
							uni.removeStorageSync('sky_token')
							this.chatMessages.push({ role: 'bot', content: '登录已过期，请重新登录后继续咨询' })
							return
						}
						if (res.statusCode === 404 && payload.thread_id) {
							this.chatThreadId = ''
							this.chatMessages.push({ role: 'bot', content: '会话已过期，请重新发送问题' })
							return
						}
						if (data.thread_id) this.chatThreadId = data.thread_id
						if (data.type === 'confirm') {
							const info = data.interrupt || {}
							uni.showModal({
								title: '操作确认',
								content: '确认执行：' + (info.action || '') + ' ' + JSON.stringify(info.args || {}),
								success: (r) => {
									this.chatMessages.push({ role: 'user', content: r.confirm ? '确认执行' : '取消操作' })
									this.doChat({ resume: !!r.confirm })
								}
							})
						} else if (data.type === 'reply') {
							this.chatMessages.push({ role: 'bot', content: data.answer || '（无回复）' })
						} else {
							this.chatMessages.push({ role: 'bot', content: '出错了：' + (data.msg || JSON.stringify(data)) })
						}
					},
					fail: () => {
						this.chatLoading = false
						this.chatMessages.push({ role: 'bot', content: '请求超时或连接失败，请稍后重试（确认 Agent 服务已启动）' })
					}
				})
			},
		...mapMutations(['setShopInfo', 'initdishListMut', 'setStoreInfo', 'setBaseUserInfo', 'setLodding', 'setSessionId']),
		...mapState(['shopInfo', 'orderListData', 'baseUserInfo', 'lodding', 'sessionId']),
		loginSync () {
			return new Promise((resolve, reject) => {
				uni.login({
					// provider: 'weixin',
					success: (loginRes) => {
						if (loginRes.errMsg === 'login:ok') {
							resolve(loginRes.code)
						}
					}
				})
			})
		},
		getData () {
			let res = { height: 0 }
			try { res = wx.getMenuButtonBoundingClientRect() } catch (e) {}
			let _this = this
			this.selectHeight = res.height
			uni.showModal({
				title: '温馨提示',
				content: '亲，授权微信登录后才能正点餐！',
				showCancel: false,
				success(res) {
					if (res.confirm) {
						// 适配改造：新版开发者工具 getUserProfile 已废弃，直接用 uni.login 拿 code 登录（开发环境后端 mock 返回已有用户），成功后保存 JWT 令牌
						uni.login({
							provider: 'weixin',
							success: (loginRes) => {
								if (loginRes.errMsg === 'login:ok') {
									userLogin({ code: loginRes.code }).then(success => {
										if (success.code === 1 && success.data && success.data.token) {
											uni.setStorageSync('sky_token', success.data.token)
											_this.init()
										}
									}).catch(err => {
									})
								}
							}
						})
						
						
						// uni.getUserProfile({
						// 	desc: '登录',
						// 	success(userInfo) {
						// 		_this.setBaseUserInfo(userInfo.rawData)
						// 		const params = {
						// 			phone: userInfo.signature,
						// 			avatar: userInfo.userInfo.avatarUrl,
						// 			name: userInfo.userInfo.nickName,
						// 			sex: userInfo.userInfo.gender
						// 		}
						// 		// 咱们自己的的接口
						// 		userLogin(params).then(success => {
						// 			if (success.code === 1) {
						// 				success.map && _this.setSessionId(success.map.sessionId)
						// 				_this.init()
						// 			}
						// 		})
						// 	},
						// 	fail: function (err) {
						// 	}
						// })
					}
				}
			})
		}, 
		
		async init () {
			// 获取菜品和套餐分类接口
			getCategoryList().then(res => {
				if (res && res.code === 1) {
					this.typeListData = [ ...res.data ]
					if (res.data.length > 0){
						this.getDishListDataes(res.data[this.typeIndex || 0])
					}
				}
			})
			// 调用一次购物车集合---初始化
			this.getTableOrderDishListes()
		},
		// 开桌操作 开桌后初始化websocket结束点餐信息
		// async openTableHandle () {
		// 	openTable({tableId: this.params.tableId, seatNumber: this.openTablePeoPleNumber}).then(res => {
		// 		this.openTablePop = false
		// 		// initWebScoket(this.params)
		// 		this.getTableOrderDishListes()
		// 		this.computOrderInfo()
		// 	}).catch(err => {
		// 	})
		// },
		// 获取菜品列表
		async getDishListDataes (params, index) {
      console.log('=-=-=-=-=-=-=getDishListDataes-=-params=-',params)
			this.rightIdAndType = {}
			this.rightIdAndType = {
				id: params.id,
				type: params.type
			}
			const param = {categoryId: params.id,type: params.type, page: 1, pageSize: 1000,status:1}
			if (params.type === 1) {
				await dishListByCategoryId(param).then(res => {
					if (res && res.code === 1) {
						// 添加一个字段去实时更新加入购物车number数量 ----- newCardNumber
						this.dishListData = res.data && res.data.map((obj) => ({ ...obj, type: 1, newCardNumber: 0 }))
					}
				}).catch(err => {
				})
			} else {
				await querySetmeaList(param).then(success => {
					if (success && success.code === 1) {
						// dishListItems被转换数组---原始this.dishListData
						this.dishListData = success.data && success.data.map((obj) => ({ ...obj, type: 2, newCardNumber: 0 }))
					}
				}).catch(err => {
				})
			}
			this.typeIndex = index
			this.setOrderNum()
		},
		// 重新拼装image
		getNewImage (image) {
			return `${baseUrl}/common/download?name=${image}`
		},
		// 获取购物车订单列表
		async getTableOrderDishListes () {
			// 调用获取购物车集合接口
			await getShoppingCartList({}).then(res => {
				if (res.code === 1) {
					this.initdishListMut(res.data)
					this.computOrderInfo()
				}
			}).catch(err => {
			})
		},
		// 去订单页面
		goOrder () {
			uni.navigateTo({url: '/pages/order/index'})
		},
		// 加菜 - 添加菜品
		async addDishAction (item, form) {
      console.log('this.flavorDataes',this.flavorDataes)
      // 规格
      if(this.openMoreNormPop && (!this.flavorDataes || this.flavorDataes.length<=0) ){
        uni.showToast({
          title: '请选择规格',
          icon: 'none',
        })
        return false
      }
      console.log('-=-=-=addDishAction-=-=-')
			// 实时更新obj.newCardNumber新添加的字段----加入购物车数量number
			// item.newCardNumber++
			if(this.orderListDataes && !this.orderListDataes.some(n => n.id == item.dishId) && this.flavorDataes.length > 0) {
				item.flavorRemark = JSON.stringify(this.flavorDataes)
			}
			// const wxUserInfo = this.baseUserInfo()
			// 有sort字段是菜品
			let params = {
				amount: item.price,
				dishFlavor: this.flavorDataes.join(','),
				number: 1 || item.dishNumber,
				name: item.name,
				image: item.image
			}
			if (item.type === 1 || item.dishId !== null) {
				params = {
					...params,
					dishId: form === '购物车' ? item.dishId : item.id
				}
			} else {
				params = {
					...params,
					setmealId: form === '购物车' ? item.setmealId : item.id
				}
			}
			newAddShoppingCartAdd(params).then(res => {
				if (res.code === 1) {
					// this.computOrderInfo()
					// this.setOrderNum()
					// 菜品详情弹框隐藏---暂时这么处理，去更新购物车状态
					this.openDetailPop = false
					this.openMoreNormPop = false
					// 调用一次购物车集合---初始化
					this.getTableOrderDishListes()
					// 重新调取刷新右侧具体菜品列表
					this.getDishListDataes(this.rightIdAndType)
				}
			}).catch(err => {
			})
		},
		// 减菜 - 添加菜品
		async redDishAction (item, form) {
			// 实时更新obj.newCardNumber新添加的字段----加入购物车数量number
			// if (item.newCardNumber === 0) {
			// 	item.newCardNumber = 0
			// } else {
			// 	item.newCardNumber--
			// }
			let params = {}
			if (item.type === 1 || item.dishId !== null) {
				params = {
					...params,
					dishId: form === '购物车' ? item.dishId : item.id
				}
			} else {
				params = {
					...params,
					setmealId: form === '购物车' ? item.setmealId : item.id
				}
			}
			await newShoppingCartSub(params).then(res => {
				if (res.code === 1) {
					// this.computOrderInfo()
					// this.setOrderNum()
					// 调用一次购物车集合---初始化
					this.getTableOrderDishListes()
					// 重新调取刷新右侧具体菜品列表
					this.getDishListDataes(this.rightIdAndType)
				}
			}).catch(err => {
			})
		},
		// 清空购物车
		clearCardOrder () {
			delShoppingCart().then(res => {
				// this.computOrderInfo()
				// this.setOrderNum()
				this.openOrderCartList = false
				// 调用一次购物车集合---初始化
				this.getTableOrderDishListes()
				// 重新调取刷新右侧具体菜品列表
				this.getDishListDataes(this.rightIdAndType)
			}).catch(err => {
			})
		},
		// 打开菜品牌详情
		openDetailHandle (item) {
			this.dishDetailes = item
			if (item.type === 2) {
				querySetmealDishById({ id: item.id }).then(res => {
					console.log(res)
					if (res.code === 1) {
						this.openDetailPop = true
						this.dishMealData = res.data
					}
				}).catch(err => {
				})
				// 老接口
				// getDishDetail({setmealId:item.dishId}).then(res => {
				// 	this.openDetailPop = true
				// 	this.dishMealData= res.data
				// }).catch(err => {
				// })
			} else {
				this.openDetailPop = true
			}
		},
		// 多规格数据处理
		moreNormDataesHandle (item) {
      this.flavorDataes.splice(0)
			this.moreNormDishdata = item
			this.openMoreNormPop = true
			this.moreNormdata = item.flavors.map(obj => ({ ...obj, value: JSON.parse(obj.value) }))
      this.moreNormdata.forEach((item)=>{
        if(item.value && item.value.length>0){
          this.flavorDataes.push(item.value[0])
        }
      })
      // this.moreNormdata = item.flavors === null ? [] : item.flavors
			// getMoreNorm({dishId: item.dishId}).then(res => {
			// 	this.openMoreNormPop = true
			// 	this.moreNormdata = res.data
			// }).catch(err => {
			// })
		},
		// 选规格 处理一行只能选择一种 
		checkMoreNormPop (obj, item) {
			let ind
			let findst = obj.some(n => {
				ind = this.flavorDataes.findIndex(o => o == n) 
				return ind != -1
			})
			const num = this.flavorDataes.findIndex(it => it == item)
			if (num == -1 && !findst){
				this.flavorDataes.push(item)
			} else if(findst) {
				this.flavorDataes.splice(ind, 1)
				this.flavorDataes.push(item)
			} else {
				this.flavorDataes.splice(num, 1)
			}
		},
		// 关闭选规格弹窗
		closeMoreNorm (moreNormDishdata) {
			this.flavorDataes.splice(0, this.flavorDataes.length)
			this.openMoreNormPop = false
		},
		// // 设置开桌人数
		// setOpenTableNumber (st) {
		// 	if (st == 'add') {
		// 		this.openTablePeoPleNumber+=1
		// 	} else {
		// 		this.openTablePeoPleNumber =  this.openTablePeoPleNumber > 1 ? this.openTablePeoPleNumber-1 : 1
		// 	}
		// },
		// 订单里和总订单价格计算
		computOrderInfo () {
			let oriData = this.orderListDataes
			this.orderDishNumber = this.orderDishPrice = 0
			oriData.map((n, i) => {
				this.orderDishNumber += n.number
				// this.orderDishPrice += n.number * n.price
				this.orderDishPrice += n.number * n.amount
			})
		},
		// 处理点餐数量 - 更新菜品已点餐数量
		setOrderNum () {
			let ODate = this.dishListData
			let CData = this.orderListDataes
			ODate && ODate.map((obj, index) => {
				obj.dishNumber = 0
				CData && CData.forEach((tg, ind) => {
					if (obj.id === tg.dishId) {
						obj.dishNumber = tg.number
						// 对新添加的实时更新newCardNumber字段进行赋值
						// obj.newCardNumber = tg.number
					}
				})
			})
			
			// this.dishListData && this.dishListData.map((obj, index) => {
			// 	obj.dishNumber = 0
			// 	this.orderListDataes && this.orderListDataes.forEach((tg, ind) => {
			// 		if (obj.id === tg.dishId) {
			// 			obj.dishNumber = tg.number
			// 		}
			// 	})
			// })
			if (this.dishListItems.length == 0) {
				this.dishListItems = ODate
			} else {
				this.dishListItems.splice(0, this.dishListItems.length, ...ODate)
			}
      console.log('-=-=-=-setOrderNum-=-=',this.dishListItems)
		},
	}
}
