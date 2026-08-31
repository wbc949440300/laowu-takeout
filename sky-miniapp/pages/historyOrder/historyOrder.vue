<template>
	<view class="history_order">
		<view class="recent_orders" v-if="recentOrdersList && recentOrdersList.length>0">
			<!-- 历史订单列表 -->
			<view class="order_lists" v-for="(item, index) in recentOrdersList" :key="index" :class="{'item-last':(Number(index)+1)===(recentOrdersList.length)}" @click="goDetail(item.id)">
				<!-- 时间和支付状态 -->
				<view class="date_type">
					<!-- 时间 -->
					<text class="time">{{ item.showTime }}</text>
					<!-- 支付状态 -->
					<text class="type" :class="{'status': item.status==2}">{{ statusWord(item.status) }}</text>
				</view>
				<!-- 点菜的内容 -->
				<view class="food_num">
					<view class="food_num_item" v-for="(num, y) in item.orderDetails" :key="y">
						<text class="food">{{ num.name }}</text>
						<text class="num">x{{ num.number }}</text>
						
					</view>
					<!-- 商品数量及金额 -->
					<view class="numAndAum">
						<text class="num_word">共{{ item.dishCount }}件商品, 实付</text>
						<text class="num_word num_price">￥{{ item.dishTotal }}</text>
					</view>
					<view class="btn" v-if="item.status === 2 || item.status === 3 || item.status === 4">
						<button class="new_btn" type="default" @click.stop="remindOrder(item.id)">催单</button>
					</view>
					<view class="btn" v-if="item.status === 5">
						<button class="new_btn" type="default" @click.stop="oneMoreOrder(item.id)">再来一单</button>
					</view>
				</view>
			</view>
      <reach-bottom v-if="finished" :status="loadingStatus"></reach-bottom>
    </view>
		<empty v-else boxHeight="100%" textLabel="暂无订单"></empty>
	</view>
</template>

<script>
import { queryOrderUserPage, oneOrderAgain, delShoppingCart, reminderOrder } from '../api/api.js'
import ReachBottom from '@/components/reach-bottom/reach-bottom.vue'
import Empty from '@/components/empty/empty'
export default {
  components:{
    ReachBottom,
		Empty
  },
	data () {
		return {
			recentOrdersList: [],
			pageInfo: {
				page: 1,
				pageSize: 10,
				total: 0
			},
			loadingType: 0,
			showTitle: false,
      finished: false,
      loadingStatus: 'complete',
		}
	},
	onLoad () {
		this.getList()
	},
	onUnload () {
		this.showTitle = false
	},
	onPullDownRefresh () {
		this.pageInfo.page = 1
		this.loadingType = 0
		this.recentOrdersList = []
    this.finished = false
		this.getList()
		uni.stopPullDownRefresh()
		this.showTitle = true
	},
	onReachBottom () {
		if (this.recentOrdersList.length < Number(this.pageInfo.total)) {
			this.pageInfo.page++
      this.loadingStatus = 'loading'
			this.getList()
			this.showTitle = true
		}
	},
	methods: {
		goDetail (id) { uni.navigateTo({ url: '/pages/orderDetail/orderDetail?id=' + id }) },
		numes (list) {
			let count = 0
			let total = 0
			(list || []).length > 0 && (list || []).forEach(obj => {
				count += Number(obj.number)
				total += Number(obj.number) * Number(obj.amount)
			})
			return { count: count, total: Number((total||0).toFixed(2)) }
		},
		statusWord (status) {
			const map = { 1: '待付款', 2: '待接单', 3: '待自提', 4: '派送中', 5: '已完成', 6: '已取消' }
			return map[status] || '未知'
		},
		getList () {
			const params = {
				pageSize: 10,
				page: this.pageInfo.page
			}
			queryOrderUserPage(params).then(res => {
				console.log('[historyOrders] code=' + res.code + ' | records=' + (res.data && res.data.records ? res.data.records.length : 'NULL') + ' | token=' + (uni.getStorageSync('sky_token') ? 'yes' : 'MISSING'))
        this.loadingStatus = 'complete'
				if (res.code === 1) {
					const records = (res.data.records || []).map(item => {
						const details = item.orderDetails || []
						let count = 0, total = 0
						details.forEach(d => { count += Number(d.number); total += Number(d.number) * Number(d.amount) })
						item.dishCount = count
						item.dishTotal = Number(total.toFixed(2))
						item.showTime = item.checkoutTime || item.orderTime || ''
						return item
					})
					this.recentOrdersList = [ ...this.recentOrdersList, ...records ]
					this.pageInfo.total = res.data.total
					// this.showTitle === true && uni.showToast({
					// 	title: '没有更多啦...',
					// 	icon: 'none',
					// 	duration: 2000
					// })
          this.finished = this.recentOrdersList.length >= Number(this.pageInfo.total)
				}
			})
		},
		remindOrder (id) {
			reminderOrder({ id }).then(res => {
				if (res.code === 1) {
					uni.showToast({ title: '已为您催单', icon: 'none' })
				} else {
					uni.showToast({ title: (res && res.msg) || '催单失败', icon: 'none' })
				}
			}).catch(err => {
				uni.showToast({ title: (err && err.msg) || '催单失败', icon: 'none' })
			})
		},
		async oneMoreOrder (id) {
			let pages = getCurrentPages()
			let routeIndex = pages.findIndex(item=>item.route==='pages/index/index')
      // 先清空购物车
      await delShoppingCart()
			oneOrderAgain({ id }).then(res => {
				if (res.code === 1) {
					uni.navigateBack({
						delta: routeIndex>-1?(pages.length-routeIndex):1
					})
					// uni.navigateBack({
					// 	url: '/pages/index/index?formOrder=' + 'oneMoreOrder'
					// })
				}
			})
		}
	}
}
</script>

<style lang="scss" scoped>
.history_order {
	// background: #f6f6f6;
	// 历史订单
	height: 100%;
	// overflow-y: auto;
	.recent_orders {
		width: 710rpx;
		border-radius: 16rpx;
		// background-color: #fff;
		margin:20rpx auto;
		padding-top: 8rpx;
		
		// 历史订单列表
		.order_lists {
			background-color: #fff;
			// margin-top: 16rpx;
			// border: 1px solid red;
			// border-radius: 16rpx;
			&:first-child{
				border-radius: 12rpx 12rpx 0 0;
				.date_type{
					border-top: 0;
				}
			}
			&.item-last{
				border-radius: 0 0 12rpx 12rpx;
			}
			.date_type {
				margin: 0 16rpx 0 28rpx;
				border-bottom: 1px dashed #efefef;
				border-top: 1px solid #efefef;
				height: 100rpx;
				
				.time {
					
					display: inline-block;
					opacity: 1;
					font-size: 28rpx;
					font-family: PingFangSC, PingFangSC-Regular;
					font-weight: 400;
					text-align: left;
					color: #333333;
					height: 100rpx;
					line-height: 100rpx;
					letter-spacing: 0px;
				}
				.type {
					display: inline-block;
					opacity: 1;
					font-size: 28rpx;
					font-family: PingFangSC, PingFangSC-Regular;
					font-weight: 400;
					text-align: left;
					color: #666666;
					height: 100rpx;
					line-height: 100rpx;
					letter-spacing: 0px;
					float: right;
					padding-right: 14rpx;
				}
				.status{
					color: #FFC200;
				}
			}
			.food_num {
				margin: 0 30rpx;
				// border-bottom: 1px solid #efefef;
				// padding-bottom: 32rpx;
				.food_num_item {
					margin-top: 20rpx;
					height: 40rpx;
					line-height: 40rpx;
					.food, .num {
						// height: 20px;
						opacity: 1;
						font-size: 28rpx;
						font-family: PingFangSC, PingFangSC-Regular;
						font-weight: 400;
						text-align: left;
						color: #666666;
						// line-height: 20px;
						letter-spacing: 0px;
					}
					.num {
						float: right;
					}
					&:first-child {
						margin-top: 30rpx;
					}
				}
				
				.numAndAum {
					text-align: right;
					padding-bottom: 40rpx;
					margin-top: 32rpx;
					padding-right: 4rpx;
					.num_word {
						height: 40rpx;
						opacity: 1;
						font-size: 28rpx;
						font-family: PingFangSC, PingFangSC-Regular;
						font-weight: 400;
						color: #666666;
						line-height: 40rpx;
						letter-spacing: 0px;
						padding-right: 4rpx;
					}
					.num_price{
						font-family: PingFangSC, PingFangSC-Medium;
						color: #333333;
					}
				}
				// 按钮部分
				.btn {
					// margin: right;
					// margin-top: 40rpx;
					height: 72rpx;
					padding-bottom: 34rpx;
					margin-right: -10rpx;
					.new_btn {
						float: right;
						width: 248rpx;
						height: 72rpx;
						line-height: 68rpx;
						border: 1px solid #e5e4e4;
						background-color: #fff;
						border-radius: 38rpx;
						font-size: 28rpx;
						font-family: PingFangSC, PingFangSC-Medium;
						font-weight: 500;
						color: #333333;
					}
				}
			}
		}
	}
}
</style>
