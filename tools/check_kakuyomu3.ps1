# TOC ページング問題の詳細確認
$headers = @{'User-Agent' = 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36'}

# テスト用URL (154話の作品)
$url = 'https://kakuyomu.jp/works/16817330649941556887'
$html = (Invoke-WebRequest -Uri $url -Headers $headers -UseBasicParsing).Content

Write-Host "=== TOC page episode link count (154 episode work) ==="
$epLinks = [regex]::Matches($html, 'href="(/works/\d+/episodes/\d+)"') | ForEach-Object { $_.Groups[1].Value } | Sort-Object -Unique
Write-Host "Unique episode links in HTML: $($epLinks.Count)"

# __NEXT_DATA__ から Episode IDを全部抽出
$ndMatch = [regex]::Match($html, '<script id="__NEXT_DATA__"[^>]*>([\s\S]+?)</script>')
if ($ndMatch.Success) {
    $json = $ndMatch.Groups[1].Value
    $epIds = [regex]::Matches($json, '"Episode:(\d+)"') | ForEach-Object { $_.Groups[1].Value } | Sort-Object -Unique
    Write-Host "Episode IDs in __NEXT_DATA__ JSON: $($epIds.Count)"
    Write-Host "First 5 episode IDs:"
    $epIds | Select-Object -First 5 | ForEach-Object { Write-Host "/works/16817330649941556887/episodes/$_" }

    # publishedAt で日付も確認
    $pubDates = [regex]::Matches($json, '"publishedAt":"([^"]+)"') | ForEach-Object { $_.Groups[1].Value } | Sort-Object -Unique
    Write-Host "publishedAt entries: $($pubDates.Count)"
    Write-Host "First 3 dates: $($pubDates | Select-Object -First 3 | Join-String -Separator ', ')"

    # TableOfContentsChapter
    $toc = [regex]::Matches($json, '"TableOfContentsChapter:([^"]+)"') | ForEach-Object { $_.Groups[1].Value } | Sort-Object -Unique
    Write-Host "TableOfContentsChapter entries: $($toc.Count)"
}

# 短い作品でテスト (5話以下の作品を探す)
Write-Host "`n=== Testing short work ==="
# カクヨムの公開されている短編作品 (適当な短編URLを試す)
$shortUrls = @(
    'https://kakuyomu.jp/works/16818093073978235490'  # top page から
)
foreach ($sUrl in $shortUrls) {
    try {
        $sHtml = (Invoke-WebRequest -Uri $sUrl -Headers $headers -UseBasicParsing).Content
        $sLinks = [regex]::Matches($sHtml, 'href="(/works/\d+/episodes/\d+)"') | ForEach-Object { $_.Groups[1].Value } | Sort-Object -Unique
        $sNd = [regex]::Match($sHtml, '<script id="__NEXT_DATA__"[^>]*>([\s\S]+?)</script>')
        if ($sNd.Success) {
            $sEpIds = [regex]::Matches($sNd.Groups[1].Value, '"Episode:(\d+)"') | ForEach-Object { $_.Groups[1].Value } | Sort-Object -Unique
            Write-Host "URL: $sUrl"
            Write-Host "  HTML links: $($sLinks.Count), JSON ep IDs: $($sEpIds.Count)"
        }
    } catch { Write-Host "Error: $sUrl - $($_.Exception.Message)" }
}

# TOC ページネーション確認 (ページ2があるか?)
Write-Host "`n=== TOC page 2 check ==="
$toc2Url = 'https://kakuyomu.jp/works/16817330649941556887?page=2'
try {
    $toc2Html = (Invoke-WebRequest -Uri $toc2Url -Headers $headers -UseBasicParsing).Content
    $toc2Links = [regex]::Matches($toc2Html, 'href="(/works/\d+/episodes/\d+)"') | ForEach-Object { $_.Groups[1].Value } | Sort-Object -Unique
    Write-Host "TOC page 2 unique links: $($toc2Links.Count)"
} catch { Write-Host "TOC page 2 error: $($_.Exception.Message)" }
