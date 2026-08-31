# 个人中心：头像/昵称无数据时用默认值兜底（新版微信不再自动给头像昵称）
$utf8 = New-Object System.Text.UTF8Encoding($false)
$f = "D:\IDEA\sky-miniapp\pages\my\my.vue"
$t = [IO.File]::ReadAllText($f, $utf8)

# 默认昵称：吃货
$chihuo = -join @(0x5403, 0x8D27 | ForEach-Object { [char]$_ })

# 1. 头像兜底 -> 店铺 logo
$oldA = "this.psersonUrl = this.`$store.state.baseUserInfo && this.`$store.state.baseUserInfo.avatarUrl"
$newA = "this.psersonUrl = (this.`$store.state.baseUserInfo && this.`$store.state.baseUserInfo.avatarUrl) || '/static/logo.png'"
if ($t.Contains("'/static/logo.png'")) {
	Write-Output "1. avatar default already"
} else {
	$t = $t.Replace($oldA, $newA)
	Write-Output ("1. avatar default: " + $t.Contains("'/static/logo.png'"))
}

# 2. 昵称兜底 -> 吃货
$oldN = "this.nickName = this.`$store.state.baseUserInfo && this.`$store.state.baseUserInfo.nickName"
$newN = "this.nickName = (this.`$store.state.baseUserInfo && this.`$store.state.baseUserInfo.nickName) || '" + $chihuo + "'"
if ($t.Contains("|| '" + $chihuo + "'")) {
	Write-Output "2. nickname default already"
} else {
	$t = $t.Replace($oldN, $newN)
	Write-Output ("2. nickname default: " + $t.Contains("|| '" + $chihuo + "'"))
}

[IO.File]::WriteAllText($f, $t, $utf8)
Write-Output "DONE"
