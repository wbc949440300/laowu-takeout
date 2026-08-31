# 修复：客服请求加超时 + 思考中点发送给提示（不再静默锁输入）
$utf8 = New-Object System.Text.UTF8Encoding($false)
$f = "D:\IDEA\sky-miniapp\pages\index\index.js"
$t = [IO.File]::ReadAllText($f, $utf8)

$thinking = -join @(0x6B63,0x5728,0x601D,0x8003,0xFF0C,0x8BF7,0x7A0D,0x5019 | ForEach-Object { [char]$_ })

# 1. 思考中点发送：给 toast 提示，不再静默
if ($t.Contains('chatLoading) { uni.showToast')) {
	Write-Output "1. toast already added"
} else {
	$t = $t.Replace("if (!text || this.chatLoading) return", ("if (!text) return`n`t`t`t`tif (this.chatLoading) { uni.showToast({ title: 'AI" + $thinking + "', icon: 'none' }); return }"))
	Write-Output ("1. toast added: " + $t.Contains('uni.showToast'))
}

# 2. 请求加 90 秒超时，防止永久卡住
if ($t.Contains('timeout: 90000')) {
	Write-Output "2. timeout already added"
} else {
	$t = $t.Replace("url: 'http://127.0.0.1:8000/agent/chat',", ("url: 'http://127.0.0.1:8000/agent/chat',`n`t`t`t`ttimeout: 90000,"))
	Write-Output ("2. timeout added: " + $t.Contains('timeout: 90000'))
}

# 3. fail 回调补充超时文案
$t = $t.Replace("content: '客服服务连接失败，请确认 Agent 服务已启动（8000 端口）'", "content: '请求超时或连接失败，请稍后重试（确认 Agent 服务已启动）'")

[IO.File]::WriteAllText($f, $t, $utf8)
Write-Output "DONE"
