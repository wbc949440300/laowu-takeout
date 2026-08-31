# 修复首页返回空白：sessionId 是 vuex state 不是方法，调用报错导致 init 不执行
# 改为基于登录令牌 sky_token 判断是否加载首页数据
$utf8 = New-Object System.Text.UTF8Encoding($false)
$f = "D:\IDEA\sky-miniapp\pages\index\index.js"
$t = [IO.File]::ReadAllText($f, $utf8)

$old = "		this.sessionId() && this.init()"
$new = @'
		// 修复：sessionId 是 vuex state 而非方法，原来当函数调用会抛错导致首页不加载数据
		// 改为根据登录令牌判断是否加载（onShow 每次显示都刷新，保证返回首页数据正常）
		if (uni.getStorageSync('sky_token')) {
			this.init()
		}
'@

if ($t.Contains("uni.getStorageSync('sky_token')) {`n`t`t`tthis.init()")) {
	Write-Output "already fixed"
} else {
	if ($t.Contains($old)) {
		$t = $t.Replace($old, $new)
		[IO.File]::WriteAllText($f, $t, $utf8)
		Write-Output ("fixed: " + (-not $t.Contains($old)))
	} else {
		Write-Output "OLD sessionId() line NOT FOUND - check manually"
	}
}
Write-Output ("sessionId() removed: " + (-not $t.Contains('this.sessionId() && this.init()')))
