$url = 'https://kakuyomu.jp/works/16817330649941556887'
$headers = @{'User-Agent' = 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36'}
$html = (Invoke-WebRequest -Uri $url -Headers $headers -UseBasicParsing).Content

Write-Host "=== HTML LENGTH ==="
Write-Host $html.Length

Write-Host "`n=== TITLE TAG ==="
[regex]::Matches($html, '<title[^>]*>[^<]+</title>') | ForEach-Object { Write-Host $_.Value }

Write-Host "`n=== H1/H2 tags ==="
[regex]::Matches($html, '<h[12][^>]*>[^<]{1,200}') | ForEach-Object {
    Write-Host $_.Value.Substring(0,[Math]::Min(200,$_.Value.Length))
}

Write-Host "`n=== All id= attributes (first 40) ==="
[regex]::Matches($html, 'id="([^"]+)"') | Select-Object -First 40 | ForEach-Object {
    Write-Host $_.Groups[1].Value
}

Write-Host "`n=== Episode href links (first 10) ==="
[regex]::Matches($html, 'href="(/works/[^"]+/episodes/[^"]+)"') | Select-Object -First 10 | ForEach-Object {
    Write-Host $_.Groups[1].Value
}

Write-Host "`n=== __NEXT_DATA__ first 1500 chars ==="
$nd = [regex]::Match($html, '<script id="__NEXT_DATA__"[^>]*>(.{1,1500})')
if ($nd.Success) { Write-Host $nd.Groups[1].Value } else { Write-Host "NOT FOUND" }

Write-Host "`n=== widget-* class names ==="
[regex]::Matches($html, 'class="([^"]*widget[^"]*)"') | Select-Object -First 20 | ForEach-Object {
    Write-Host $_.Groups[1].Value
}

Write-Host "`n=== meta og: tags ==="
[regex]::Matches($html, '<meta[^>]+(og:[^"]+|twitter:[^"]+)[^>]*>') | Select-Object -First 10 | ForEach-Object {
    Write-Host $_.Value.Substring(0,[Math]::Min(200,$_.Value.Length))
}
