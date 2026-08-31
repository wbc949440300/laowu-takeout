# 客服按钮：改名"AI客服" + 位置上移避开结算栏 + 改胶囊样式
$utf8 = New-Object System.Text.UTF8Encoding($false)
$f = "D:\IDEA\sky-miniapp\pages\index\index.vue"
$t = [IO.File]::ReadAllText($f, $utf8)

$kefu = -join @(0x5BA2, 0x670D | ForEach-Object { [char]$_ })
$aiKefu = "AI" + $kefu

# 1. 按钮文字：客服 -> AI客服
$t = $t.Replace('">' + $kefu + '</view>', '">' + $aiKefu + '</view>')

# 2. 弹窗标题：智能客服 -> AI 智能客服
$zhineng = -join @(0x667A, 0x80FD, 0x5BA2, 0x670D | ForEach-Object { [char]$_ })
$t = $t.Replace('>' + $zhineng + '</text>', '>AI ' + $zhineng + '</text>')

# 3. 按钮位置上移 + 胶囊样式（避开底部结算栏）
$old = '.cs_entry { position: fixed; right: 30rpx; bottom: 200rpx; width: 100rpx; height: 100rpx; border-radius: 50%; background: #ffc200; color: #333; display: flex; align-items: center; justify-content: center; font-size: 28rpx; box-shadow: 0 4rpx 16rpx rgba(0,0,0,0.2); z-index: 999; }'
$new = '.cs_entry { position: fixed; right: 30rpx; bottom: 320rpx; padding: 16rpx 28rpx; border-radius: 40rpx; background: #ffc200; color: #333; display: flex; align-items: center; justify-content: center; font-size: 26rpx; box-shadow: 0 4rpx 16rpx rgba(0,0,0,0.25); z-index: 997; }'
if ($t.Contains($old)) {
	$t = $t.Replace($old, $new)
} else {
	# 兜底：只替换关键样式
	$t = $t.Replace('bottom: 200rpx; width: 100rpx; height: 100rpx; border-radius: 50%;', 'bottom: 320rpx; padding: 16rpx 28rpx; border-radius: 40rpx;')
}

[IO.File]::WriteAllText($f, $t, $utf8)

Write-Output ("AI客服 text: " + $t.Contains($aiKefu))
Write-Output ("title AI 智能客服: " + $t.Contains('AI ' + $zhineng))
Write-Output ("button moved up: " + $t.Contains('bottom: 320rpx'))
