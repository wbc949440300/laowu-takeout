# 历史订单：去重 console.log，换成更有用的诊断行
$utf8 = New-Object System.Text.UTF8Encoding($false)
$f = "D:\IDEA\sky-miniapp\pages\historyOrder\historyOrder.vue"
$t = [IO.File]::ReadAllText($f, $utf8)

$two = "				console.log('[historyOrders] response:', JSON.stringify(res))`n				console.log('[historyOrders] response:', JSON.stringify(res))"
$one = "				console.log('[historyOrders] code=' + res.code + ' | records=' + (res.data && res.data.records ? res.data.records.length : 'NULL') + ' | token=' + (uni.getStorageSync('sky_token') ? 'yes' : 'MISSING'))"

if ($t.Contains($two)) {
	$t = $t.Replace($two, $one)
	[IO.File]::WriteAllText($f, $t, $utf8)
	Write-Output "deduped + enhanced"
} else {
	Write-Output "two-line pattern not found (may already be single)"
	# 兜底：若只有一行，替换它
	$single = "				console.log('[historyOrders] response:', JSON.stringify(res))"
	if ($t.Contains($single)) {
		$t = $t.Replace($single, $one)
		[IO.File]::WriteAllText($f, $t, $utf8)
		Write-Output "single line enhanced"
	} else {
		Write-Output "no console.log found at all"
	}
}
Write-Output ("has diagnostic: " + $t.Contains('[historyOrders] code='))
