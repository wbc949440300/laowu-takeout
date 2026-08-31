# 历史订单加调试日志：打印接口返回，定位空列表原因
$utf8 = New-Object System.Text.UTF8Encoding($false)
$f = "D:\IDEA\sky-miniapp\pages\historyOrder\historyOrder.vue"
$t = [IO.File]::ReadAllText($f, $utf8)

if ($t.Contains("console.log('[historyOrders]'")) {
	Write-Output "debug log already added"
} else {
	$t = $t.Replace("			queryOrderUserPage(params).then(res => {", ("			queryOrderUserPage(params).then(res => {`n				console.log('[historyOrders] response:', JSON.stringify(res))"))
	[IO.File]::WriteAllText($f, $t, $utf8)
	Write-Output ("debug log added: " + $t.Contains("console.log('[historyOrders]'"))
}
