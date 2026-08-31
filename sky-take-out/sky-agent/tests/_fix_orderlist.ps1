# 修复历史订单页：空状态文案"暂无地址"->"暂无订单" + 加错误处理
$utf8 = New-Object System.Text.UTF8Encoding($false)
$f = "D:\IDEA\sky-miniapp\pages\historyOrder\historyOrder.vue"
$t = [IO.File]::ReadAllText($f, $utf8)

$noOrder = -join @(0x6682,0x65E0,0x8BA2,0x5355 | ForEach-Object { [char]$_ })
$noAddr = -join @(0x6682,0x65E0,0x5730,0x5740 | ForEach-Object { [char]$_ })

# 1. 空状态文案
if ($t.Contains($noOrder)) {
	Write-Output "1. text already fixed"
} else {
	$t = $t.Replace(('textLabel="' + $noAddr + '"'), ('textLabel="' + $noOrder + '"'))
	Write-Output ("1. text fixed: " + $t.Contains($noOrder))
}

# 2. getList 加错误处理（请求失败时提示，不再静默空状态）
$loadFail = -join @(0x8BA2,0x5355,0x52A0,0x8F7D,0x5931,0x8D25,0xFF0C,0x8BF7,0x91CD,0x65B0,0x767B,0x5F55 | ForEach-Object { [char]$_ })
$oldList = "			queryOrderUserPage(params).then(res => {"
if ($t.Contains('.catch')) {
	Write-Output "2. catch already added"
} else {
	# 在 getList 的 then 链末尾加 catch
	$t = $t.Replace("          this.finished = this.recentOrdersList.length >= Number(this.pageInfo.total)`n				}`n			})", ("          this.finished = this.recentOrdersList.length >= Number(this.pageInfo.total)`n				} else {`n					uni.showToast({ title: '" + $loadFail + "', icon: 'none' })`n				}`n			}).catch(err => {`n				uni.showToast({ title: '" + $loadFail + "', icon: 'none' })`n			})"))
	Write-Output ("2. catch added: " + $t.Contains('.catch'))
}

[IO.File]::WriteAllText($f, $t, $utf8)
Write-Output "DONE"
