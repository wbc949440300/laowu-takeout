# 打通支付：① api.js 支付接口改为 PUT /order/payment  ② 下单成功后调用支付（mock 直接标记已支付）
$utf8 = New-Object System.Text.UTF8Encoding($false)

# ---------- 1. api.js：payOrder 改为对接后端支付接口 ----------
$f = "D:\IDEA\sky-miniapp\pages\api\api.js"
$t = [IO.File]::ReadAllText($f, $utf8)
if ($t.Contains("url: '/order/payment'")) {
	Write-Output "1. api.js payOrder already fixed"
} else {
	$pattern = 'export const payOrder = \(params\) =>\s*request\(\{\s*url: [^,]+,\s*method: ''GET'',\s*params\s*\}\)'
	$repl = "export const payOrder = (params) =>`n`trequest({`n`t`turl: '/order/payment',`n`t`tmethod: 'PUT',`n`t`tparams`n`t})"
	$t2 = [regex]::Replace($t, $pattern, $repl)
	if ($t2 -eq $t) {
		Write-Output "1. api.js pattern NOT matched"
	} else {
		$t = $t2
		[IO.File]::WriteAllText($f, $t, $utf8)
		Write-Output ("1. api.js payOrder fixed: " + $t.Contains("url: '/order/payment'"))
	}
}

# ---------- 2. order/index.js：提交订单成功后调用支付 ----------
$f = "D:\IDEA\sky-miniapp\pages\order\index.js"
$t = [IO.File]::ReadAllText($f, $utf8)
if ($t.Contains('payOrder({ orderNumber')) {
	Write-Output "2. payOrderHandle already calls payment"
} else {
	$old = "						// uni.navigateTo({url: '/pages/order/success'})`n						uni.redirectTo({`n							url: '/pages/order/success'`n						})"
	$new = @'
						const orderNumber = res.data && res.data.orderNumber
						// 提交成功后调用支付（开发环境为 mock 支付，后端直接标记已支付）
						payOrder({ orderNumber: orderNumber, payMethod: 1 }).then(() => {
							uni.redirectTo({ url: '/pages/order/success' })
						}).catch(() => {
							uni.redirectTo({ url: '/pages/order/success' })
						})
'@
	if ($t.Contains($old)) {
		$t = $t.Replace($old, $new)
		[IO.File]::WriteAllText($f, $t, $utf8)
		Write-Output ("2. payment wired: " + $t.Contains('payOrder({ orderNumber'))
	} else {
		Write-Output "2. submit block NOT matched - check manually"
	}
}
Write-Output "DONE"
