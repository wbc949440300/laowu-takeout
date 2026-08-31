# 修复：把客服三件套挪进根元素内部并用 cs_wrap 包裹（Vue2 只能有一个根元素）
$utf8 = New-Object System.Text.UTF8Encoding($false)
$f = "D:\IDEA\sky-miniapp\pages\index\index.vue"
$t = [IO.File]::ReadAllText($f, $utf8)

if ($t.Contains('cs_wrap')) {
	Write-Output "already wrapped, skip"
	exit
}

$start = $t.IndexOf('<view class="cs_entry"')
if ($start -lt 0) { Write-Output "cs_entry NOT FOUND"; exit }

# cs 区块结束位置：cs_panel 的闭合 </view>（即 </template> 前最后一个 </view>）
$endTemplate = $t.IndexOf('</template>')
$endCs = $t.LastIndexOf('</view>', $endTemplate) + '</view>'.Length
$csBlock = $t.Substring($start, $endCs - $start)

# 从原位置剪掉
$t = $t.Remove($start, $endCs - $start)

# 包上 cs_wrap，插到根元素闭合标签之前（剪掉后，根元素闭合标签=</template> 前最后一个 </view>）
$endTemplate = $t.IndexOf('</template>')
$rootClose = $t.LastIndexOf('</view>', $endTemplate)
$wrapped = "<view class=`"cs_wrap`">" + $csBlock + "</view>"
$t = $t.Insert($rootClose, $wrapped)

[IO.File]::WriteAllText($f, $t, $utf8)

# 校验
Write-Output ("cs_wrap count: " + ([regex]::Matches($t, 'cs_wrap')).Count)
Write-Output ("template open/close: " + ([regex]::Matches($t, '<template>')).Count + "/" + ([regex]::Matches($t, '</template>')).Count)
Write-Output ("view balance check: open=" + ([regex]::Matches($t, '<view')).Count + " close=" + ([regex]::Matches($t, '</view>')).Count)
