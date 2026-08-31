# 头像兜底路径修正：/static/ -> ../../static/（与页面其他图片一致的相对路径）
$utf8 = New-Object System.Text.UTF8Encoding($false)
$f = "D:\IDEA\sky-miniapp\pages\my\my.vue"
$t = [IO.File]::ReadAllText($f, $utf8)

if ($t.Contains("'../../static/logo.png'")) {
	Write-Output "already relative path"
} else {
	$t = $t.Replace("'/static/logo.png'", "'../../static/logo.png'")
	[IO.File]::WriteAllText($f, $t, $utf8)
	Write-Output ("changed to relative: " + $t.Contains("'../../static/logo.png'"))
}
