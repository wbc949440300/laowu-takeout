# 修复小程序价格显示：后端价格单位是"元"，老代码按"分"多除了 100，全部去掉 /100
$utf8 = New-Object System.Text.UTF8Encoding($false)
$root = "D:\IDEA\sky-miniapp"
$totalFixed = 0

function Fix-File($relPath, $pairs) {
	$f = Join-Path $root $relPath
	$t = [IO.File]::ReadAllText($f, $utf8)
	$count = 0
	foreach ($p in $pairs) {
		if ($t.Contains($p[0])) { $t = $t.Replace($p[0], $p[1]); $count++ }
	}
	[IO.File]::WriteAllText($f, $t, $utf8)
	Write-Output ($relPath + " fixed " + $count + " spots")
	return $count
}

$c1 = Fix-File "pages\index\index.vue" @(
	, @('{{ item.price / 100 }}', '{{ item.price.toFixed(2) }}'),
	, @('{{orderDishPrice / 100+6}}', '{{(orderDishPrice+6).toFixed(2)}}'),
	, @('{{moreNormDishdata.price / 100}}', '{{moreNormDishdata.price.toFixed(2)}}'),
	, @('{{dishDetailes.price / 100}}', '{{dishDetailes.price.toFixed(2)}}'),
	, @('{{ obj.amount / 100 }}', '{{ obj.amount.toFixed(2) }}')
)

$c2 = Fix-File "pages\order\index.vue" @(
	, @('{{ obj.amount / 100 }}', '{{ obj.amount.toFixed(2) }}'),
	, @('{{orderDishPrice / 100+6}}', '{{(orderDishPrice+6).toFixed(2)}}')
)

$c3 = Fix-File "pages\historyOrder\historyOrder.vue" @(
	, @('return { count: count, total: (total/100) }', 'return { count: count, total: Number(total.toFixed(2)) }')
)

$c4 = Fix-File "pages\my\my.vue" @(
	, @('amount += item.amount/100', 'amount += item.amount')
)

$totalFixed = $c1 + $c2 + $c3 + $c4
Write-Output ("TOTAL fixed: " + $totalFixed + " spots")

# 验证：确认没有遗漏的 /100 价格逻辑
$left = Get-ChildItem $root -Include *.vue,*.js -Recurse -File | Where-Object { $_.FullName -notmatch "unpackage" } | Select-String -Pattern "price ?/ ?100|amount ?/ ?100|total/100"
if ($left) { Write-Output "LEFTOVER FOUND:"; $left | ForEach-Object { $_.Path + ":" + $_.LineNumber } } else { Write-Output "no leftover /100 price logic" }
