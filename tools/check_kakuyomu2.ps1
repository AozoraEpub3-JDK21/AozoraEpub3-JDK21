# TOC ページの詳細確認
$url = 'https://kakuyomu.jp/works/16817330649941556887'
$headers = @{'User-Agent' = 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36'}
$html = (Invoke-WebRequest -Uri $url -Headers $headers -UseBasicParsing).Content
$enc = [System.Text.Encoding]::UTF8

Write-Host "=== TITLE tag (UTF-8) ==="
$titleRaw = [regex]::Match($html, '<title[^>]*>([^<]+)</title>').Groups[1].Value
Write-Host $titleRaw

Write-Host "`n=== Episode link count ==="
$episodeLinks = [regex]::Matches($html, 'href="(/works/\d+/episodes/\d+)"')
Write-Host "Found $($episodeLinks.Count) episode href occurrences"
$uniqueLinks = $episodeLinks | ForEach-Object { $_.Groups[1].Value } | Sort-Object -Unique
Write-Host "Unique links: $($uniqueLinks.Count)"

Write-Host "`n=== First 5 unique episode links ==="
$uniqueLinks | Select-Object -First 5 | ForEach-Object { Write-Host $_ }

Write-Host "`n=== Anchor tags near episode links (first 3, up to 300 chars) ==="
[regex]::Matches($html, '<a[^>]+href="/works/\d+/episodes/\d+[^"]*"[^>]*>[^<]{0,100}') | Select-Object -First 3 | ForEach-Object {
    Write-Host $_.Value.Substring(0, [Math]::Min(300, $_.Value.Length))
    Write-Host "---"
}

Write-Host "`n=== __NEXT_DATA__ search for 'title' and author info ==="
$ndMatch = [regex]::Match($html, '<script id="__NEXT_DATA__"[^>]*>([\s\S]+?)</script>')
if ($ndMatch.Success) {
    $json = $ndMatch.Groups[1].Value
    # Work title
    $titleMatch = [regex]::Match($json, '"Work:\d+":\{"__typename":"Work","id":"\d+","title":"([^"]+)"')
    if ($titleMatch.Success) {
        Write-Host "Work title in JSON: $($titleMatch.Groups[1].Value)"
    }
    # Author name (activityName / name)
    $authorMatch = [regex]::Match($json, '"activityName":"([^"]+)"')
    if ($authorMatch.Success) { Write-Host "activityName: $($authorMatch.Groups[1].Value)" }
    $nameMatch = [regex]::Match($json, '"UserAccount[^"]*":\{"[^}]*"name":"([^"]+)"')
    if ($nameMatch.Success) { Write-Host "UserAccount name: $($nameMatch.Groups[1].Value)" }

    # Introduction/description
    $introMatch = [regex]::Match($json, '"introduction":"([^"]{1,200})')
    if ($introMatch.Success) { Write-Host "Introduction: $($introMatch.Groups[1].Value)" }

    # JSON length
    Write-Host "JSON total length: $($json.Length)"
    Write-Host "JSON first 2000 chars:"
    Write-Host $json.Substring(0, [Math]::Min(2000, $json.Length))
}

# Episode page check
Write-Host "`n=== EPISODE PAGE STRUCTURE ==="
$epUrl = 'https://kakuyomu.jp/works/16817330649941556887/episodes/16817330650193350811'
$epHtml = (Invoke-WebRequest -Uri $epUrl -Headers $headers -UseBasicParsing).Content
Write-Host "Episode HTML length: $($epHtml.Length)"

Write-Host "`n--- Episode: class names containing 'episode' or 'body' or 'content' ---"
[regex]::Matches($epHtml, 'class="([^"]*(?:episode|body|content|title)[^"]*)"') | Select-Object -First 20 | ForEach-Object {
    Write-Host $_.Groups[1].Value
}

Write-Host "`n--- Episode: id attributes ---"
[regex]::Matches($epHtml, 'id="([^"]+)"') | Select-Object -First 20 | ForEach-Object {
    Write-Host $_.Groups[1].Value
}

Write-Host "`n--- Episode: p tags (first 5, up to 200 chars) ---"
[regex]::Matches($epHtml, '<p[^>]*>[^<]{10,200}') | Select-Object -First 5 | ForEach-Object {
    Write-Host $_.Value.Substring(0, [Math]::Min(200, $_.Value.Length))
    Write-Host "---"
}
