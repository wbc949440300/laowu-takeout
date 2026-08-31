# 前端 numes 空值保护：防止 orderDetails 缺失时 undefined.length 崩溃
$utf8 = New-Object System.Text.UTF8Encoding($false)
$f = "D:\IDEA\sky-miniapp\pages\historyOrder\historyOrder.vue"
$t = [IO.File]::ReadAllText($f, $utf8)

if ($t.Contains("(list || []).length")) {
	Write-Output "already safe"
} else {
	$t = $t.Replace("list.length > 0 && list.forEach(obj => {", "(list || []).length > 0 && (list || []).forEach(obj => {")
	[IO.File]::WriteAllText($f, $t, $utf8)
	Write-Output ("numes safe: " + $t.Contains("(list || []).length"))
}
