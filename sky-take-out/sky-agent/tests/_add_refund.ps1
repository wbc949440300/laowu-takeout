# 订单详情页加"申请退款"按钮 + api 接口
$utf8 = New-Object System.Text.UTF8Encoding($false)

# ---------- 1. api.js 加申请退款接口 ----------
$f = "D:\IDEA\sky-miniapp\pages\api\api.js"
$t = [IO.File]::ReadAllText($f, $utf8)
if ($t.Contains('applyRefundOrder')) {
	Write-Output "1. api applyRefundOrder exists"
} else {
	$fn = @'

// 申请退款
export const applyRefundOrder = (id, reason) => {
	return request({
		url: `/order/refund/apply/${id}`,
		method: 'POST',
		params: { reason }
	})
}
'@
	$t = $t + $fn
	[IO.File]::WriteAllText($f, $t, $utf8)
	Write-Output ("1. api applyRefundOrder added: " + $t.Contains('applyRefundOrder'))
}

# ---------- 2. orderDetail.vue ----------
$f = "D:\IDEA\sky-miniapp\pages\orderDetail\orderDetail.vue"
$t = [IO.File]::ReadAllText($f, $utf8)

# 2a. import
$t = $t.Replace("import { getOrderDetail } from '../api/api.js'", "import { getOrderDetail, applyRefundOrder } from '../api/api.js'")

# 2b. 模板：金额卡片后加退款按钮
$btnHtml = @'
			<view class="od_actions" v-if="order.status === 2 || order.status === 3 || order.status === 4">
				<button class="od_refund_btn" @click="applyRefund">申请退款</button>
			</view>
'@
if ($t.Contains('od_refund_btn')) {
	Write-Output "2b. refund button exists"
} else {
	$pattern = '(<view class="od_row"><text class="od_label">打包费</text>[\s\S]*?</view>\s*</view>)'
	$t = [regex]::Replace($t, $pattern, ('$1' + "`n" + $btnHtml), 1)
	Write-Output ("2b. refund button added: " + $t.Contains('od_refund_btn'))
}

# 2c. methods 开头插入 applyRefund
if ($t.Contains('applyRefund ()')) {
	Write-Output "2c. applyRefund method exists"
} else {
	$method = @'
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
'@
	$t = [regex]::Replace($t, '(methods: \{)', ('$1' + "`n" + $method), 1)
	Write-Output ("2c. applyRefund method added: " + $t.Contains('applyRefund ()'))
}

# 2d. style
if (-not $t.Contains('.od_refund_btn')) {
	$style = @'
	.od_actions { margin-bottom: 20rpx; }
	.od_refund_btn { background: #fff; color: #e64340; border: 1rpx solid #e64340; border-radius: 40rpx; font-size: 28rpx; line-height: 72rpx; }
'@
	$t = $t.Replace('</style>', ($style + '</style>'))
}

[IO.File]::WriteAllText($f, $t, $utf8)
Write-Output ("FINAL: import=" + $t.Contains('applyRefundOrder }') + " | btn=" + $t.Contains('od_refund_btn') + " | method=" + $t.Contains('applyRefund ()') + " | style=" + $t.Contains('.od_refund_btn {'))
