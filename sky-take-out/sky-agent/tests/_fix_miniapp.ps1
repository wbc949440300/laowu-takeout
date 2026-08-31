# 适配 sky-miniapp 到老吴外卖后端：接口前缀、JWT 鉴权、登录流程
$utf8 = New-Object System.Text.UTF8Encoding($false)
$root = "D:\IDEA\sky-miniapp"

# 1. env.js: baseUrl 加 /user 前缀（后端用户端接口统一在 /user/** 下）
$f = "$root\utils\env.js"
$t = [IO.File]::ReadAllText($f, $utf8)
$t = $t.Replace("export const baseUrl = 'http://localhost:8080'", "export const baseUrl = 'http://localhost:8080/user'")
[IO.File]::WriteAllText($f, $t, $utf8)
Write-Output ("1. env.js -> " + $t.Contains("localhost:8080/user"))

# 2. request.js: Cookie 会话改为 JWT authentication 头
$f = "$root\utils\request.js"
$t = [IO.File]::ReadAllText($f, $utf8)
$t = $t.Replace("Cookie: 'JSESSIONID=' + storeInfo.sessionId", "'authentication': uni.getStorageSync('sky_token') || ''")
[IO.File]::WriteAllText($f, $t, $utf8)
Write-Output ("2. request.js -> " + $t.Contains("'authentication':"))

# 3. api.js: 历史订单与再来一单路径适配后端
$f = "$root\pages\api\api.js"
$t = [IO.File]::ReadAllText($f, $utf8)
$t = $t.Replace("url: '/order/userPage',", "url: '/order/historyOrders',")
$t = $t.Replace("url: '/order/again',", 'url: `/order/repetition/${params.id}`,')
[IO.File]::WriteAllText($f, $t, $utf8)
Write-Output ("3. api.js -> historyOrders=" + $t.Contains("historyOrders") + " repetition=" + $t.Contains("repetition"))

# 4. index.js: 登录流程改造（getUserProfile 已废弃，直接 uni.login + 保存 JWT）
$f = "$root\pages\index\index.js"
$t = [IO.File]::ReadAllText($f, $utf8)
$pattern = "(?s)if \(res\.confirm\) \{\s*let jsCode.*?fail: function \(err\) \{\s*\}\s*\}\)"
$newBlock = @'
if (res.confirm) {
						// 适配改造：新版开发者工具 getUserProfile 已废弃，直接用 uni.login 拿 code 登录（开发环境后端 mock 返回已有用户），成功后保存 JWT 令牌
						uni.login({
							provider: 'weixin',
							success: (loginRes) => {
								if (loginRes.errMsg === 'login:ok') {
									userLogin({ code: loginRes.code }).then(success => {
										if (success.code === 1 && success.data && success.data.token) {
											uni.setStorageSync('sky_token', success.data.token)
											_this.init()
										}
									}).catch(err => {
									})
								}
							}
						})
'@
$t2 = [regex]::Replace($t, $pattern, $newBlock)
if ($t2 -eq $t) { Write-Output "4. index.js -> PATTERN NOT MATCHED!" } else {
	[IO.File]::WriteAllText($f, $t2, $utf8)
	Write-Output ("4. index.js -> " + $t2.Contains("setStorageSync('sky_token'"))
}
