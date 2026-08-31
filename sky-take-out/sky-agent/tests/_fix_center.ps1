# 客服弹窗改为屏幕居中：不再占据底部，居中悬浮显示
$utf8 = New-Object System.Text.UTF8Encoding($false)
$f = "D:\IDEA\sky-miniapp\pages\index\index.vue"
$t = [IO.File]::ReadAllText($f, $utf8)

$old = '.cs_panel { position: fixed; left: 0; right: 0; bottom: 0; height: 60vh; background: #f5f6fa; border-radius: 24rpx 24rpx 0 0; display: flex; flex-direction: column; z-index: 999; }'
$new = '.cs_panel { position: fixed; left: 50%; top: 50%; transform: translate(-50%, -50%); width: 90%; height: 60vh; background: #f5f6fa; border-radius: 24rpx; display: flex; flex-direction: column; z-index: 999; box-shadow: 0 8rpx 40rpx rgba(0,0,0,0.3); }'

if ($t.Contains('transform: translate(-50%')) {
	Write-Output "already centered"
} else {
	if ($t.Contains($old)) {
		$t = $t.Replace($old, $new)
		Write-Output "panel centered"
	} else {
		# 兜底：只替换定位相关属性
		$t = $t.Replace('.cs_panel { position: fixed; left: 0; right: 0; bottom: 0; height: 60vh;', '.cs_panel { position: fixed; left: 50%; top: 50%; transform: translate(-50%, -50%); width: 90%; height: 60vh;')
		$t = $t.Replace('border-radius: 24rpx 24rpx 0 0;', 'border-radius: 24rpx; box-shadow: 0 8rpx 40rpx rgba(0,0,0,0.3);')
		Write-Output "panel centered (fallback)"
	}
	[IO.File]::WriteAllText($f, $t, $utf8)
}
Write-Output ("centered check: " + $t.Contains('transform: translate(-50%'))
