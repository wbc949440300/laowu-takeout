# 订单详情：创建详情页 + pages.json 注册 + api.js 增加接口函数
$utf8 = New-Object System.Text.UTF8Encoding($false)
$root = "D:\IDEA\sky-miniapp"

# ---------- 1. 创建详情页 ----------
$odVue = @'
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
		</view>
		<view v-else class="od_loading">加载中...</view>
	</view>
</template>

<script>
	import { getOrderDetail } from '../api/api.js'
	export default {
		data() {
			return { order: null }
		},
		onLoad(options) {
			if (options && options.id) this.loadDetail(options.id)
		},
		methods: {
			statusWord(status) {
				const map = { 1: '待付款', 2: '待接单', 3: '待自提', 4: '派送中', 5: '已完成', 6: '已取消' }
				return map[status] || '未知'
			},
			loadDetail(id) {
				getOrderDetail({ id }).then(res => {
					if (res && res.code === 1) this.order = res.data
				}).catch(() => {
					uni.showToast({ title: '加载失败', icon: 'none' })
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
</style>
'@
$odDir = "$root\pages\orderDetail"
if (!(Test-Path $odDir)) { New-Item -ItemType Directory -Path $odDir | Out-Null }
[IO.File]::WriteAllText("$odDir\orderDetail.vue", $odVue, $utf8)
Write-Output "1. orderDetail.vue created"

# ---------- 2. pages.json 注册 ----------
$f = "$root\pages.json"
$pj = [IO.File]::ReadAllText($f, $utf8)
if ($pj.Contains('pages/orderDetail/orderDetail')) {
	Write-Output "2. pages.json already registered"
} else {
	$title = -join @(0x8BA2,0x5355,0x8BE6,0x60C5 | ForEach-Object { [char]$_ })
	$entry = '{ "path": "pages/orderDetail/orderDetail", "style": { "navigationBarTitleText": "' + $title + '" } },'
	$pj = $pj.Replace('"pages": [', ('"pages": [' + "`n    " + $entry))
	[IO.File]::WriteAllText($f, $pj, $utf8)
	Write-Output ("2. pages.json registered: " + $pj.Contains('pages/orderDetail/orderDetail'))
}

# ---------- 3. api.js 增加详情接口 ----------
$f = "$root\pages\api\api.js"
$api = [IO.File]::ReadAllText($f, $utf8)
if ($api.Contains('getOrderDetail')) {
	Write-Output "3. api.js already has getOrderDetail"
} else {
	$fn = @'

// 订单详情（用户端）
export const getOrderDetail = (params) => {
	return request({
		url: `/order/orderDetail/${params.id}`,
		method: 'GET',
		params
	})
}
'@
	$api = $api + $fn
	[IO.File]::WriteAllText($f, $api, $utf8)
	Write-Output ("3. api.js getOrderDetail added: " + $api.Contains('getOrderDetail'))
}
Write-Output "DONE"
