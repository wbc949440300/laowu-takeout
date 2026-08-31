# 订单详情页兜底：无 id / 加载失败时自动跳回首页，避免编译入口停在详情页卡住
$utf8 = New-Object System.Text.UTF8Encoding($false)
$f = "D:\IDEA\sky-miniapp\pages\orderDetail\orderDetail.vue"
$t = [IO.File]::ReadAllText($f, $utf8)

# 1. onLoad：无 id 时跳回首页
$oldLoad = "			if (options && options.id) this.loadDetail(options.id)"
$newLoad = @'
			if (options && options.id) {
				this.loadDetail(options.id)
			} else {
				// 兜底：没有订单 id（编译入口直接打开本页）时跳回首页，避免卡在加载中
				uni.reLaunch({ url: '/pages/index/index' })
			}
'@
if ($t.Contains('uni.reLaunch({ url: ''/pages/index/index'' })')) {
	Write-Output "1. onLoad fallback already"
} else {
	$t = $t.Replace($oldLoad, $newLoad)
	Write-Output ("1. onLoad fallback: " + $t.Contains('uni.reLaunch'))
}

# 2. loadDetail 成功但 code!=1：跳回首页
$oldOk = "					if (res && res.code === 1) this.order = res.data"
$newOk = @'
					if (res && res.code === 1) {
						this.order = res.data
					} else {
						uni.reLaunch({ url: '/pages/index/index' })
					}
'@
$t = $t.Replace($oldOk, $newOk)

# 3. catch：提示后跳回首页
$fail = -join @(0x52A0,0x8F7D,0x5931,0x8D25 | ForEach-Object { [char]$_ })
$oldCatch = "					uni.showToast({ title: '" + $fail + "', icon: 'none' })"
$newCatch = $oldCatch + "`n					setTimeout(() => { uni.reLaunch({ url: '/pages/index/index' }) }, 800)"
if ($t.Contains('setTimeout(() => { uni.reLaunch')) {
	Write-Output "3. catch redirect already"
} else {
	$t = $t.Replace($oldCatch, $newCatch)
	Write-Output ("3. catch redirect: " + $t.Contains('setTimeout(() => { uni.reLaunch'))
}

[IO.File]::WriteAllText($f, $t, $utf8)
Write-Output ("FINAL: reLaunch count=" + ([regex]::Matches($t, 'uni.reLaunch')).Count)
