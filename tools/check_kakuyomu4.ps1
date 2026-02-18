# __NEXT_DATA__ JSON の Episode順序確認
$headers = @{'User-Agent' = 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36'}
$url = 'https://kakuyomu.jp/works/16817330649941556887'
$html = (Invoke-WebRequest -Uri $url -Headers $headers -UseBasicParsing).Content

$ndMatch = [regex]::Match($html, '<script id="__NEXT_DATA__"[^>]*>([\s\S]+?)</script>')
$json = $ndMatch.Groups[1].Value

# Episode:ID の出現順序を記録 (ユニーク)
Write-Host "=== Episode IDs in JSON order (first 20, deduped) ==="
$seen = @{}
$ordered = [System.Collections.Generic.List[string]]::new()
$allEpMatches = [regex]::Matches($json, '"Episode:(\d+)"')
foreach ($m in $allEpMatches) {
    $id = $m.Groups[1].Value
    if (-not $seen.ContainsKey($id)) {
        $seen[$id] = $true
        $ordered.Add($id)
    }
}
Write-Host "Total unique episodes: $($ordered.Count)"
$ordered | Select-Object -First 20 | ForEach-Object { Write-Host $_ }

# Episode の publishedAt 情報を確認 (最初の3件)
Write-Host "`n=== Episode details (first 3) ==="
for ($i = 0; $i -lt [Math]::Min(3, $ordered.Count); $i++) {
    $epId = $ordered[$i]
    $detailMatch = [regex]::Match($json, [regex]::Escape("""Episode:$epId""") + ':\{([^}]{1,500})')
    if ($detailMatch.Success) {
        $detail = $detailMatch.Groups[1].Value
        $titleM = [regex]::Match($detail, '"title":"([^"]+)"')
        $dateM = [regex]::Match($detail, '"publishedAt":"([^"]+)"')
        Write-Host "Episode ${epId}:"
        if ($titleM.Success) { Write-Host "  title: $($titleM.Groups[1].Value)" }
        if ($dateM.Success) { Write-Host "  publishedAt: $($dateM.Groups[1].Value)" }
    }
}

# TableOfContentsChapter の構造確認
Write-Host "`n=== TableOfContentsChapter structure (first chapter) ==="
$tocMatch = [regex]::Match($json, '"TableOfContentsChapter:([^"]+)":\{([^}]{1,800})')
if ($tocMatch.Success) {
    Write-Host "Chapter key: $($tocMatch.Groups[1].Value)"
    Write-Host "Content: $($tocMatch.Groups[2].Value.Substring(0, [Math]::Min(500, $tocMatch.Groups[2].Value.Length)))"
}

# Work object 確認 (title, author ref)
Write-Host "`n=== Work object ==="
$workMatch = [regex]::Match($json, '"Work:\d+":\{([^}]{1,1000})')
if ($workMatch.Success) {
    Write-Host $workMatch.Groups[1].Value.Substring(0, [Math]::Min(500, $workMatch.Groups[1].Value.Length))
}

# activityName 全ての出現
Write-Host "`n=== activityName occurrences ==="
[regex]::Matches($json, '"activityName":"([^"]+)"') | ForEach-Object {
    Write-Host $_.Groups[1].Value
}

# introduction
Write-Host "`n=== introduction ==="
$introM = [regex]::Match($json, '"introduction":"([^"]{1,300})')
if ($introM.Success) { Write-Host $introM.Groups[1].Value }
