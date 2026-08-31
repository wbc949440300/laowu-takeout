# 历史订单：订单卡片加点击进详情 + 修复顶部 null（显示订单时间）
$utf8 = New-Object System.Text.UTF8Encoding($false)
$f = "D:\IDEA\sky-miniapp\pages\historyOrder\historyOrder.vue"
$t = [IO.File]::ReadAllText($f, $utf8)

# 1. 订单卡片加 @click
$cardOld = '<view class="order_lists" v-for="(item, index) in recentOrdersList" :key="index" :class="{''item-last'':(Number(index)+1)===(recentOrdersList.length)}">'
$cardNew = $cardOld.Substring(0, $cardOld.Length - 1) + ' @click="goDetail(item.id)">'
if ($t.Contains('goDetail(item.id)')) {
	Write-Output "1. card click already added"
} else {
	if ($t.Contains($cardOld)) {
		$t = $t.Replace($cardOld, $cardNew)
		Write-Output "1. card click added"
	} else {
		Write-Output "1. CARD NOT FOUND - check manually"
	}
}

# 2. 顶部 null 修复：显示订单时间（模板）
if ($t.Contains('{{ item.showTime }}')) {
	Write-Output "2. template showTime already"
} else {
	$t = $t.Replace('{{ item.checkoutTime }}', '{{ item.showTime }}')
	Write-Output ("2. template showTime: " + $t.Contains('{{ item.showTime }}'))
}

# 3. getList 预计算 showTime
if ($t.Contains('item.showTime =')) {
	Write-Output "3. showTime precompute already"
} else {
	$t = $t.Replace('						item.dishTotal = Number(total.toFixed(2))', ('						item.dishTotal = Number(total.toFixed(2))' + "`n" + '						item.showTime = item.checkoutTime || item.orderTime || ' + "''"))
	Write-Output ("3. showTime precompute: " + $t.Contains('item.showTime ='))
}

# 4. methods 加 goDetail
if ($t.Contains('goDetail (id)')) {
	Write-Output "4. goDetail already"
} else {
	$t = $t.Replace("	methods: {", ("	methods: {`n		goDetail (id) { uni.navigateTo({ url: '/pages/orderDetail/orderDetail?id=' + id }) },"))
	Write-Output ("4. goDetail added: " + $t.Contains('goDetail (id)'))
}

[IO.File]::WriteAllText($f, $t, $utf8)
Write-Output ("FINAL: click=" + $t.Contains('goDetail(item.id)') + " | showTime=" + $t.Contains('{{ item.showTime }}') + " | goDetail=" + $t.Contains('goDetail (id)'))
