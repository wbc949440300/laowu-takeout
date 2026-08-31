# 对 D:\IDEA\sky-miniapp 应用联调修改：接口地址、品牌名、WebSocket 地址
$root = "D:\IDEA\sky-miniapp"
$strictUtf8 = New-Object System.Text.UTF8Encoding($false, $true)
$utf8 = New-Object System.Text.UTF8Encoding($false)
$gbk = [Text.Encoding]::GetEncoding("GBK")

function ReadAuto($f) {
    $bytes = [IO.File]::ReadAllBytes($f)
    try { $text = $strictUtf8.GetString($bytes); return @($text, $utf8, "UTF-8") }
    catch { return @($gbk.GetString($bytes), $gbk, "GBK") }
}

# 1. env.js baseUrl
$f = "$root\utils\env.js"
$r = ReadAuto $f; $text = $r[0]; $enc = $r[1]
$text = [regex]::Replace($text, "export const baseUrl = '[^']*'", "export const baseUrl = 'http://localhost:8080'")
[IO.File]::WriteAllText($f, $text, $enc)
Write-Output ("env.js [" + $r[2] + "] -> baseUrl=http://localhost:8080")

# 2. webscoket.js url
$f = "$root\utils\webscoket.js"
$r = ReadAuto $f
$text = [regex]::Replace($r[0], "url: 'wss?://[^']*'", "url: 'ws://localhost:8080/ws/1'")
[IO.File]::WriteAllText($f, $text, $r[1])
Write-Output ("webscoket.js [" + $r[2] + "] -> ws://localhost:8080/ws/1")

# 3. 品牌名：瑞吉外卖/苍穹外卖 -> 老吴外卖
$oldBrands = @( (-join @(0x745E,0x5409,0x5916,0x9001 | % { [char]$_ })), (-join @(0x82CD,0x7A79,0x5916,0x5356 | % { [char]$_ })) )
$newBrand = -join @(0x8001,0x5434,0x5916,0x5356 | % { [char]$_ })
$files = @("$root\pages.json", "$root\pages\index\index.vue", "$root\pages\index\index.js")
foreach ($f in $files) {
    if (!(Test-Path $f)) { continue }
    $r = ReadAuto $f; $text = $r[0]; $total = 0
    foreach ($b in $oldBrands) { $n = ([regex]::Matches($text, [regex]::Escape($b))).Count; $total += $n; $text = $text.Replace($b, $newBrand) }
    # 单字品牌兜底：页面标题可能只有"瑞吉"或"苍穹"
    foreach ($b in @($oldBrands[0].Substring(0,2), $oldBrands[1].Substring(0,2))) { $n = ([regex]::Matches($text, [regex]::Escape($b))).Count; $total += $n; $text = $text.Replace($b, $newBrand) }
    [IO.File]::WriteAllText($f, $text, $r[1])
    Write-Output ($f.Replace($root,"") + " [" + $r[2] + "] replaced=" + $total)
}

# 4. 验证
$r = ReadAuto "$root\pages.json"
Write-Output ("verify pages.json has new brand = " + $r[0].Contains($newBrand))
$r = ReadAuto "$root\utils\env.js"
Write-Output ("verify env.js localhost = " + $r[0].Contains("localhost:8080"))
try { $null = ($r = ReadAuto "$root\pages.json"); Write-Output "pages.json readable OK" } catch { Write-Output "pages.json CORRUPT" }
