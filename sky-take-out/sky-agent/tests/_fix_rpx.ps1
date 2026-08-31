# 弹窗定位修复：vh 单位在小程序不生效，改用 rpx 固定高度
$utf8 = New-Object System.Text.UTF8Encoding($false)
$f = "D:\IDEA\sky-miniapp\pages\index\index.vue"
$t = [IO.File]::ReadAllText($f, $utf8)

# 当前（vh 版）
$vhVer = '.cs_panel { position: fixed; left: 5%; right: 5%; top: 20vh; height: 60vh; background: #f5f6fa; border-radius: 24rpx; display: flex; flex-direction: column; z-index: 999; box-shadow: 0 8rpx 40rpx rgba(0,0,0,0.3); overflow: hidden; }'
# 改为 rpx 固定高度（iPhone 12/13 屏高约1624rpx：top 20%≈325rpx, height 60%≈975rpx）
$rpxVer = '.cs_panel { position: fixed; left: 37rpx; right: 37rpx; top: 325rpx; height: 975rpx; background: #f5f6fa; border-radius: 24rpx; display: flex; flex-direction: column; z-index: 999; box-shadow: 0 8rpx 40rpx rgba(0,0,0,0.3); overflow: hidden; }'

if ($t.Contains('top: 325rpx')) {
	Write-Output "already rpx"
} else {
	if ($t.Contains($vhVer)) {
		$t = $t.Replace($vhVer, $rpxVer)
		Write-Output "switched to rpx"
	} else {
		# 兜底：正则替换 top/height 的 vh
		$t = [regex]::Replace($t, 'top: \d+vh; height: \d+vh;', 'top: 325rpx; height: 975rpx;')
		$t = [regex]::Replace($t, 'left: \d+%; right: \d+%;', 'left: 37rpx; right: 37rpx;')
		Write-Output "switched (regex)"
	}
	[IO.File]::WriteAllText($f, $t, $utf8)
}
Write-Output ("rpx check: " + $t.Contains('top: 325rpx'))

# scroll-view 确保不被内容撑开：固定高度 = 弹窗高 - 标题栏 - 输入栏
# cs_msgs 若还是 flex:1 可能失效，补一个明确的 max-height 兜底
if ($t.Contains('.cs_msgs { flex: 1')) {
	$t = $t.Replace('.cs_msgs { flex: 1; padding: 20rpx; box-sizing: border-box; }', '.cs_msgs { flex: 1; height: 0; padding: 20rpx; box-sizing: border-box; }')
	[IO.File]::WriteAllText($f, $t, $utf8)
	Write-Output ("cs_msgs height:0 flex trick: " + $t.Contains('height: 0'))
} else {
	Write-Output "cs_msgs not found (check manually)"
}
