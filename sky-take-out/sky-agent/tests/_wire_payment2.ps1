# order/index.js 支付接线（正则版，不依赖换行缩进）
$utf8 = New-Object System.Text.UTF8Encoding($false)
$f = "D:\IDEA\sky-miniapp\pages\order\index.js"
$t = [IO.File]::ReadAllText($f, $utf8)

if ($t.Contains('payOrder({ orderNumber')) {
	Write-Output "already wired"
} else {
	$pattern = "//\s*uni\.navigateTo\(\{url:\s*'/pages/order/success'\}\)\s*uni\.redirectTo\(\{\s*url:\s*'/pages/order/success'\s*\}\)"
	$new = @'
const orderNumber = res.data && res.data.orderNumber
						payOrder({ orderNumber: orderNumber, payMethod: 1 }).then(() => {
							uni.redirectTo({ url: '/pages/order/success' })
						}).catch(() => {
							uni.redirectTo({ url: '/pages/order/success' })
						})
'@
	$t2 = [regex]::Replace($t, $pattern, $new, 1)
	if ($t2 -eq $t) {
		Write-Output "regex NOT matched"
	} else {
		[IO.File]::WriteAllText($f, $t2, $utf8)
		Write-Output ("payment wired: " + $t2.Contains('payOrder({ orderNumber'))
	}
}
