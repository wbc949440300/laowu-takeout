# 修复：键盘弹出挡住输入框，加 adjust-position + cursor-spacing 让输入框自动顶到键盘上方
$utf8 = New-Object System.Text.UTF8Encoding($false)
$f = "D:\IDEA\sky-miniapp\pages\index\index.vue"
$t = [IO.File]::ReadAllText($f, $utf8)

if ($t.Contains('cursor-spacing')) {
	Write-Output "already fixed"
} else {
	$old = '<input class="cs_input" v-model="chatInput" placeholder="输入问题，回车发送" confirm-type="send" @confirm="sendChatMsg" />'
	$new = '<input class="cs_input" v-model="chatInput" placeholder="输入问题，回车发送" confirm-type="send" @confirm="sendChatMsg" :adjust-position="true" :cursor-spacing="15" :hold-keyboard="true" />'
	if ($t.Contains($old)) {
		$t = $t.Replace($old, $new)
		Write-Output "input attrs added"
	} else {
		Write-Output "input tag NOT FOUND (format mismatch)"
	}
	[IO.File]::WriteAllText($f, $t, $utf8)
}

# 弹窗高度从 70vh 降到 60vh，给键盘留更多空间，避免输入框被挤出去
$t = [IO.File]::ReadAllText($f, $utf8)
if ($t.Contains('height: 60vh')) {
	Write-Output "panel height already 60vh"
} else {
	$t = $t.Replace('.cs_panel { position: fixed; left: 0; right: 0; bottom: 0; height: 70vh;', '.cs_panel { position: fixed; left: 0; right: 0; bottom: 0; height: 60vh;')
	[IO.File]::WriteAllText($f, $t, $utf8)
	Write-Output ("panel height 60vh: " + $t.Contains('height: 60vh'))
}
