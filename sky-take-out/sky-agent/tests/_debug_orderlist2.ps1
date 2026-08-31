# 历史订单加调试日志（不带前导空白匹配）
$utf8 = New-Object System.Text.UTF8Encoding($false)
$f = "D:\IDEA\sky-miniapp\pages\historyOrder\historyOrder.vue"
$t = [IO.File]::ReadAllText($f, $utf8)

if ($t.Contains("console.log('[historyOrders]'")) {
	Write-Output "debug log already added"
} else {
	$anchor = "queryOrderUserPage(params).then(res => {"
	$repl = $anchor + "`n				console.log('[historyOrders] response:', JSON.stringify(res))"
	$t = $t.Replace($anchor, $repl)
	[IO.File]::WriteAllText($f, $t, $utf8)
	Write-Output ("debug log added: " + $t.Contains("console.log('[historyOrders]'"))
}
