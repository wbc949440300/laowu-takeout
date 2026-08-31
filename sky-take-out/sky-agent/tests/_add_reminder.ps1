# 历史订单：加催单按钮 + 修正状态码映射（对齐后端1-6）+ 再来一单改为已完成显示
$utf8 = New-Object System.Text.UTF8Encoding($false)

# ---------- 1. api.js 加催单接口 ----------
$f = "D:\IDEA\sky-miniapp\pages\api\api.js"
$t = [IO.File]::ReadAllText($f, $utf8)
if ($t.Contains('reminderOrder')) {
	Write-Output "1. api reminderOrder exists"
} else {
	$fn = @'

// 客户催单
export const reminderOrder = (params) => {
	return request({
		url: `/order/reminder/${params.id}`,
		method: 'GET',
		params
	})
}
'@
	$t = $t + $fn
	[IO.File]::WriteAllText($f, $t, $utf8)
	Write-Output ("1. api reminderOrder added: " + $t.Contains('reminderOrder'))
}

# ---------- 2. historyOrder.vue ----------
$f = "D:\IDEA\sky-miniapp\pages\historyOrder\historyOrder.vue"
$t = [IO.File]::ReadAllText($f, $utf8)

# 2a. import 加 reminderOrder
$t = $t.Replace("import { queryOrderUserPage, oneOrderAgain, delShoppingCart } from '../api/api.js'", "import { queryOrderUserPage, oneOrderAgain, delShoppingCart, reminderOrder } from '../api/api.js'")

# 2b. 状态码映射对齐后端（正则整体替换）
$swPattern = 'statusWord \(status\) \{[\s\S]*?\n\t\t\},'
$swNew = @'
statusWord (status) {
			const map = { 1: '待付款', 2: '待接单', 3: '待自提', 4: '派送中', 5: '已完成', 6: '已取消' }
			return map[status] || '未知'
		},
'@
$t = [regex]::Replace($t, $swPattern, $swNew, 1)

# 2c. 按钮区：催单(2/3/4) + 再来一单(5)
$btnOld = @'
					<view class="btn" v-if="item.status === 4">
						<button class="new_btn" type="default" @click="oneMoreOrder(item.id)">再来一单</button>
					</view>
'@
$btnNew = @'
					<view class="btn" v-if="item.status === 2 || item.status === 3 || item.status === 4">
						<button class="new_btn" type="default" @click="remindOrder(item.id)">催单</button>
					</view>
					<view class="btn" v-if="item.status === 5">
						<button class="new_btn" type="default" @click="oneMoreOrder(item.id)">再来一单</button>
					</view>
'@
if ($t.Contains($btnOld)) {
	$t = $t.Replace($btnOld, $btnNew)
	Write-Output "2c. buttons added"
} else {
	Write-Output "2c. BUTTON BLOCK NOT MATCHED"
}

# 2d. 加 remindOrder 方法（插在 oneMoreOrder 前）
if ($t.Contains('remindOrder (id)')) {
	Write-Output "2d. remindOrder exists"
} else {
	$remind = @'
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
'@
	$t = $t.Replace("		async oneMoreOrder (id) {", $remind)
	Write-Output ("2d. remindOrder added: " + $t.Contains('remindOrder (id)'))
}

[IO.File]::WriteAllText($f, $t, $utf8)
Write-Output ("FINAL: import=" + $t.Contains('reminderOrder }') + " | statusMap=" + $t.Contains("5: '已完成'") + " | remindBtn=" + $t.Contains('remindOrder(item.id)') + " | oneMore5=" + $t.Contains('item.status === 5'))
