# 历史订单：改为数据预计算，避免模板内调用方法导致的编译渲染问题
$utf8 = New-Object System.Text.UTF8Encoding($false)
$f = "D:\IDEA\sky-miniapp\pages\historyOrder\historyOrder.vue"
$t = [IO.File]::ReadAllText($f, $utf8)

# 1. 模板：numes(...).count/.total 改为直接读预计算字段
if ($t.Contains('{{ item.dishCount }}')) {
	Write-Output "1. template already uses precomputed"
} else {
	$t = $t.Replace('{{ numes(item.orderDetails).count }}', '{{ item.dishCount }}')
	$t = $t.Replace('{{ numes(item.orderDetails).total }}', '{{ item.dishTotal }}')
	Write-Output ("1. template precomputed: " + $t.Contains('{{ item.dishCount }}'))
}

# 2. getList：接收数据时预计算 dishCount / dishTotal
$oldAssign = "					this.recentOrdersList = [ ...this.recentOrdersList, ...res.data.records ]"
$newAssign = @'
					const records = (res.data.records || []).map(item => {
						const details = item.orderDetails || []
						let count = 0, total = 0
						details.forEach(d => { count += Number(d.number); total += Number(d.number) * Number(d.amount) })
						item.dishCount = count
						item.dishTotal = Number(total.toFixed(2))
						return item
					})
					this.recentOrdersList = [ ...this.recentOrdersList, ...records ]
'@
if ($t.Contains('item.dishTotal = Number')) {
	Write-Output "2. getList already precomputes"
} else {
	if ($t.Contains($oldAssign)) {
		$t = $t.Replace($oldAssign, $newAssign)
		Write-Output "2. getList precompute added"
	} else {
		Write-Output "2. OLD ASSIGN NOT FOUND - check manually"
	}
}

[IO.File]::WriteAllText($f, $t, $utf8)
Write-Output ("final: dishCount in template=" + $t.Contains('{{ item.dishCount }}') + " | precompute in getList=" + $t.Contains('item.dishTotal = Number'))
