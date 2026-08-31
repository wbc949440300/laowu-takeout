# 修复：Vue2 模板只能有一个根元素，用 cs_wrap 包住客服三件套
$utf8 = New-Object System.Text.UTF8Encoding($false)
$f = "D:\IDEA\sky-miniapp\pages\index\index.vue"
$t = [IO.File]::ReadAllText($f, $utf8)

if ($t.Contains('cs_wrap')) {
	Write-Output "already wrapped"
} else {
	# 在 cs_entry 前加包裹层开始标签
	$t = $t.Replace('<view class="cs_entry" @click="toggleChat">', '<view class="cs_wrap"><view class="cs_entry" @click="toggleChat">')
	# 在模板结束前闭合包裹层（</view></template> 只在模板末尾出现一次）
	$t = [regex]::Replace($t, '</view>\s*</template>', "</view></view>`n</template>", 1)
	[IO.File]::WriteAllText($f, $t, $utf8)
	Write-Output ("wrapped: " + $t.Contains('cs_wrap'))
}

# 校验根元素数量：数 <template> 之后第一层子元素没法静态数，但至少确认 cs_wrap 存在且唯一
Write-Output ("cs_wrap count: " + ([regex]::Matches($t, 'cs_wrap')).Count)
Write-Output ("template open: " + ([regex]::Matches($t, '<template>')).Count + " close: " + ([regex]::Matches($t, '</template>')).Count)
