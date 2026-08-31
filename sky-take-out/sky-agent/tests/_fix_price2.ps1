# 修复价格 /100（简化版：直接字符串替换）
$utf8 = New-Object System.Text.UTF8Encoding($false)
$root = "D:\IDEA\sky-miniapp"

$f = "$root\pages\index\index.vue"
$t = [IO.File]::ReadAllText($f, $utf8)
$t = $t.Replace('{{ item.price / 100 }}', '{{ item.price.toFixed(2) }}')
$t = $t.Replace('{{orderDishPrice / 100+6}}', '{{(orderDishPrice+6).toFixed(2)}}')
$t = $t.Replace('{{moreNormDishdata.price / 100}}', '{{moreNormDishdata.price.toFixed(2)}}')
$t = $t.Replace('{{dishDetailes.price / 100}}', '{{dishDetailes.price.toFixed(2)}}')
$t = $t.Replace('{{ obj.amount / 100 }}', '{{ obj.amount.toFixed(2) }}')
[IO.File]::WriteAllText($f, $t, $utf8)
Write-Output ("index.vue leftover /100: " + ([regex]::Matches($t, "/ ?100")).Count)

$f = "$root\pages\order\index.vue"
$t = [IO.File]::ReadAllText($f, $utf8)
$t = $t.Replace('{{ obj.amount / 100 }}', '{{ obj.amount.toFixed(2) }}')
$t = $t.Replace('{{orderDishPrice / 100+6}}', '{{(orderDishPrice+6).toFixed(2)}}')
[IO.File]::WriteAllText($f, $t, $utf8)
Write-Output ("order/index.vue leftover /100: " + ([regex]::Matches($t, "/ ?100")).Count)

# 全局检查
$left = Get-ChildItem $root -Include *.vue,*.js -Recurse -File | Where-Object { $_.FullName -notmatch "unpackage" } | Select-String -Pattern "price ?/ ?100|amount ?/ ?100|total/100|/ ?100\+|/ ?100\}\}"
if ($left) { Write-Output "LEFTOVER:"; $left | ForEach-Object { $_.Path.Replace($root,"") + ":" + $_.LineNumber + ": " + $_.Line.Trim() } } else { Write-Output "ALL CLEAN" }
