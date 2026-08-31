# 三合一修复：①个人中心订单卡片可点击  ②按钮阻止冒泡  ③再来一单状态码修正+my状态映射
$utf8 = New-Object System.Text.UTF8Encoding($false)

# ---------- historyOrder.vue：按钮加 .stop 阻止冒泡 ----------
$f = "D:\IDEA\sky-miniapp\pages\historyOrder\historyOrder.vue"
$t = [IO.File]::ReadAllText($f, $utf8)
$t = $t.Replace('@click="remindOrder(item.id)"', '@click.stop="remindOrder(item.id)"')
$t = $t.Replace('@click="oneMoreOrder(item.id)"', '@click.stop="oneMoreOrder(item.id)"')
[IO.File]::WriteAllText($f, $t, $utf8)
Write-Output ("historyOrder stop: remind=" + $t.Contains('@click.stop="remindOrder') + " | oneMore=" + $t.Contains('@click.stop="oneMoreOrder'))

# ---------- my.vue ----------
$f = "D:\IDEA\sky-miniapp\pages\my\my.vue"
$t = [IO.File]::ReadAllText($f, $utf8)

# 1. 最近订单卡片加点击跳详情
$t = $t.Replace('<view class="order_lists" v-for="(item, index) in recentOrdersList" :key="index">', '<view class="order_lists" v-for="(item, index) in recentOrdersList" :key="index" @click="goDetail(item.id)">')

# 2. 加 goDetail 方法（插在 goOrder 前）
if (-not $t.Contains('goDetail (id)')) {
	$gd = @'
		goDetail (id) {
			uni.navigateTo({ url: '/pages/orderDetail/orderDetail?id=' + id })
		},
		goOrder () {
'@
	$t = $t.Replace("		goOrder () {", $gd)
}

# 3. 状态码映射对齐后端
$swPattern = 'statusWord \(status\) \{[\s\S]*?\n\t\t\},'
$swNew = @'
statusWord (status) {
			const map = { 1: '待付款', 2: '待接单', 3: '待自提', 4: '派送中', 5: '已完成', 6: '已取消' }
			return map[status] || '未知'
		},
'@
$t = [regex]::Replace($t, $swPattern, $swNew, 1)

# 4. 再来一单状态码 4 -> 5
$t = $t.Replace('<view class="againBtn" v-if="item.status === 4">', '<view class="againBtn" v-if="item.status === 5">')

[IO.File]::WriteAllText($f, $t, $utf8)
Write-Output ("my.vue: cardClick=" + $t.Contains('@click="goDetail(item.id)"') + " | goDetail=" + $t.Contains('goDetail (id)') + " | statusMap=" + $t.Contains("5: '已完成'") + " | again5=" + $t.Contains('againBtn" v-if="item.status === 5"'))
