# 弹窗定位修复：放弃 transform 居中（小程序兼容性差），改固定定位（top 20vh + 高 60vh）
$utf8 = New-Object System.Text.UTF8Encoding($false)
$f = "D:\IDEA\sky-miniapp\pages\index\index.vue"
$t = [IO.File]::ReadAllText($f, $utf8)

$centered = '.cs_panel { position: fixed; left: 50%; top: 50%; transform: translate(-50%, -50%); width: 90%; height: 60vh; background: #f5f6fa; border-radius: 24rpx; display: flex; flex-direction: column; z-index: 999; box-shadow: 0 8rpx 40rpx rgba(0,0,0,0.3); }'
$fixed = '.cs_panel { position: fixed; left: 5%; right: 5%; top: 20vh; height: 60vh; background: #f5f6fa; border-radius: 24rpx; display: flex; flex-direction: column; z-index: 999; box-shadow: 0 8rpx 40rpx rgba(0,0,0,0.3); overflow: hidden; }'

if ($t.Contains('left: 5%; right: 5%; top: 20vh')) {
	Write-Output "already fixed positioning"
} else {
	if ($t.Contains($centered)) {
		$t = $t.Replace($centered, $fixed)
		Write-Output "switched to fixed positioning"
	} else {
		# 兜底：正则替换 transform 相关
		$t = [regex]::Replace($t, 'left: 50%; top: 50%; transform: translate\(-50%, -50%\); width: 90%;', 'left: 5%; right: 5%; top: 20vh;')
		Write-Output "switched (regex fallback)"
	}
	[IO.File]::WriteAllText($f, $t, $utf8)
}
Write-Output ("fixed positioning check: " + $t.Contains('left: 5%; right: 5%; top: 20vh'))

# 确认 scroll-view 有高度约束（flex:1 + 父级固定高度即可滚动）
Write-Output ("cs_msgs flex: " + $t.Contains('.cs_msgs { flex: 1'))
