<template>
	<view class="od_page">
		<view v-if="order">
			<view class="od_status">{{ statusWord(order.status) }}</view>
			<view class="od_card">
				<view class="od_row"><text class="od_label">订单号</text><text>{{ order.number }}</text></view>
				<view class="od_row"><text class="od_label">下单时间</text><text>{{ order.orderTime }}</text></view>
				<view class="od_row"><text class="od_label">收货人</text><text>{{ order.consignee }}</text></view>
				<view class="od_row"><text class="od_label">手机号</text><text>{{ order.phone }}</text></view>
				<view class="od_row"><text class="od_label">地址</text><text>{{ order.address }}</text></view>
			</view>
			<view class="od_card">
				<view class="od_dish" v-for="(d, i) in (order.orderDetails || [])" :key="i">
					<text class="od_name">{{ d.name }}</text>
					<text class="od_num">x{{ d.number }}</text>
					<text class="od_amount">¥{{ (d.amount || 0) }}</text>
				</view>
			</view>
			<view class="od_card">
				<view class="od_row"><text class="od_label">商品总额</text><text>¥{{ order.amount }}</text></view>
				<view class="od_row"><text class="od_label">打包费</text><text>¥{{ order.packAmount || 0 }}</text></view>
			</view>
			<view class="od_actions" v-if="order.status === 2 || order.status === 3 || order.status === 4">
				<button class="od_refund_btn" @click="applyRefund">申请退款</button>
			</view>
		</view>
		<view v-else class="od_loading">加载中...</view>
	</view>
</template>

<script>
	import { getOrderDetail, applyRefundOrder } from '../api/api.js'
	export default {
		data() {
			return { order: null }
		},
		onLoad(options) {
			if (options && options.id) {
				this.loadDetail(options.id)
			} else {
				// 兜底：没有订单 id（编译入口直接打开本页）时跳回首页，避免卡在加载中
				uni.reLaunch({ url: '/pages/index/index' })
			}
		},
		methods: {
		applyRefund () {
			uni.showModal({
				title: '申请退款',
				content: '确定要对该订单申请退款吗？',
				success: (r) => {
					if (r.confirm) {
						applyRefundOrder(this.order.id, '用户申请退款').then(res => {
							if (res.code === 1) {
								uni.showToast({ title: '退款申请已提交', icon: 'none' })
							} else {
								uni.showToast({ title: res.msg || '申请失败', icon: 'none' })
							}
						}).catch(err => {
							uni.showToast({ title: (err && err.msg) || '申请失败', icon: 'none' })
						})
					}
				}
			})
		},
			statusWord(status) {
				const map = { 1: '待付款', 2: '待接单', 3: '待自提', 4: '派送中', 5: '已完成', 6: '已取消' }
				return map[status] || '未知'
			},
			loadDetail(id) {
				getOrderDetail({ id }).then(res => {
					if (res && res.code === 1) {
						this.order = res.data
					} else {
						uni.reLaunch({ url: '/pages/index/index' })
					}
				}).catch(() => {
					uni.showToast({ title: '加载失败', icon: 'none' })
					setTimeout(() => { uni.reLaunch({ url: '/pages/index/index' }) }, 800)
				})
			}
		}
	}
</script>

<style>
	.od_page { padding: 20rpx; background: #f5f6fa; min-height: 100vh; box-sizing: border-box; }
	.od_status { font-size: 36rpx; font-weight: bold; color: #333; padding: 20rpx 10rpx; }
	.od_card { background: #fff; border-radius: 16rpx; padding: 20rpx; margin-bottom: 20rpx; }
	.od_row { display: flex; justify-content: space-between; padding: 12rpx 0; font-size: 28rpx; color: #333; }
	.od_label { color: #999; }
	.od_dish { display: flex; align-items: center; padding: 12rpx 0; font-size: 28rpx; }
	.od_name { flex: 1; color: #333; }
	.od_num { width: 80rpx; color: #666; }
	.od_amount { width: 120rpx; text-align: right; color: #333; }
	.od_loading { text-align: center; color: #999; padding: 100rpx 0; }
	.od_actions { margin-bottom: 20rpx; }
	.od_refund_btn { background: #fff; color: #e64340; border: 1rpx solid #e64340; border-radius: 40rpx; font-size: 28rpx; line-height: 72rpx; }</style>