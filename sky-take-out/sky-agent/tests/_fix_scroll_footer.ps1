# 1. 消息列表自动滚动到底部  2. 恢复底部结算栏（居中弹窗不占底部）
$utf8 = New-Object System.Text.UTF8Encoding($false)

# ---------- index.js：加 chatScrollTop + watch 自动滚动 ----------
$f = "D:\IDEA\sky-miniapp\pages\index\index.js"
$t = [IO.File]::ReadAllText($f, $utf8)

if ($t.Contains('chatScrollTop')) {
	Write-Output "1. scroll already added"
} else {
	# data 里加 chatScrollTop
	$t = $t.Replace("chatLoading: false,", ("chatLoading: false,`n`t`t`tchatScrollTop: 0,"))
	# methods 前插入 watch：消息变化时自动滚到底
	$t = $t.Replace("`tmethods: {", ("`twatch: {`n`t`tchatMessages() { this.`$nextTick(() => { this.chatScrollTop = this.chatScrollTop === 99999 ? 100000 : 99999 }) }`n`t},`n`tmethods: {"))
	[IO.File]::WriteAllText($f, $t, $utf8)
	Write-Output ("1. chatScrollTop: " + $t.Contains('chatScrollTop') + " watch: " + $t.Contains('watch:'))
}

# ---------- index.vue：scroll-view 绑定 + 恢复结算栏 ----------
$f = "D:\IDEA\sky-miniapp\pages\index\index.vue"
$t = [IO.File]::ReadAllText($f, $utf8)

# scroll-view 绑定 scroll-top 实现自动滚动
if ($t.Contains(':scroll-top="chatScrollTop"')) {
	Write-Output "2. scroll-view already bound"
} else {
	$t = $t.Replace('<scroll-view class="cs_msgs" scroll-y>', '<scroll-view class="cs_msgs" scroll-y :scroll-top="chatScrollTop" :scroll-with-animation="true">')
	Write-Output ("2. scroll-view bound: " + $t.Contains(':scroll-top="chatScrollTop"'))
}

# 恢复结算栏（去掉 !showChat 隐藏，居中弹窗不再遮挡底部）
$t = $t.Replace('v-if="!showChat && orderListData().length === 0"', 'v-if="orderListData().length === 0"')
$t = $t.Replace('<view class="footer_order_buttom order_form" v-else-if="!showChat">', '<view class="footer_order_buttom order_form" v-else>')

[IO.File]::WriteAllText($f, $t, $utf8)
Write-Output ("3. footer restored: " + (-not $t.Contains('!showChat && orderListData')))
