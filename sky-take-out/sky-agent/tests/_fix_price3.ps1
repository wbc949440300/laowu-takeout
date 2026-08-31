# 修复 toFixed 空值崩溃：undefined.toFixed(2) 会抛错导致页面空白，全部改成空值安全写法
$utf8 = New-Object System.Text.UTF8Encoding($false)
$root = "D:\IDEA\sky-miniapp"

$f = "$root\pages\index\index.vue"
$t = [IO.File]::ReadAllText($f, $utf8)
$t = $t.Replace('{{ item.price.toFixed(2) }}', '{{ (item.price || 0).toFixed(2) }}')
$t = $t.Replace('{{(orderDishPrice+6).toFixed(2)}}', '{{((orderDishPrice||0)+6).toFixed(2)}}')
$t = $t.Replace('{{moreNormDishdata.price.toFixed(2)}}', '{{((moreNormDishdata && moreNormDishdata.price) || 0).toFixed(2)}}')
$t = $t.Replace('{{dishDetailes.price.toFixed(2)}}', '{{((dishDetailes && dishDetailes.price) || 0).toFixed(2)}}')
$t = $t.Replace('{{ obj.amount.toFixed(2) }}', '{{ (obj.amount || 0).toFixed(2) }}')
[IO.File]::WriteAllText($f, $t, $utf8)
Write-Output ("index.vue unsafe toFixed left: " + ([regex]::Matches($t, "(?<!\|\| 0\))\.toFixed")).Count)

$f = "$root\pages\order\index.vue"
$t = [IO.File]::ReadAllText($f, $utf8)
$t = $t.Replace('{{ obj.amount.toFixed(2) }}', '{{ (obj.amount || 0).toFixed(2) }}')
$t = $t.Replace('{{(orderDishPrice+6).toFixed(2)}}', '{{((orderDishPrice||0)+6).toFixed(2)}}')
[IO.File]::WriteAllText($f, $t, $utf8)
Write-Output ("order/index.vue unsafe toFixed left: " + ([regex]::Matches($t, "(?<!\|\| 0\))\.toFixed")).Count)

$f = "$root\pages\historyOrder\historyOrder.vue"
$t = [IO.File]::ReadAllText($f, $utf8)
$t = $t.Replace('Number(total.toFixed(2))', 'Number((total||0).toFixed(2))')
[IO.File]::WriteAllText($f, $t, $utf8)
Write-Output "historyOrder.vue done"

# 全局扫描残留的不安全 toFixed（前面没有 || 0 保护的）
$left = Get-ChildItem $root -Include *.vue,*.js -Recurse -File | Where-Object { $_.FullName -notmatch "unpackage" } | Select-String -Pattern "\.toFixed" | Where-Object { $_.Line -notmatch "\|\| 0" }
if ($left) { Write-Output "REMAINING toFixed lines:"; $left | ForEach-Object { $_.Path.Replace($root,"") + ":" + $_.LineNumber + ": " + $_.Line.Trim().Substring(0, [Math]::Min(80, $_.Line.Trim().Length)) } } else { Write-Output "ALL SAFE" }
