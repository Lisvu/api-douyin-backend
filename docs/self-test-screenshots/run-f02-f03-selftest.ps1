# F02/F03 推荐流接口自测脚本（使用 curl.exe，避免 PowerShell 代理导致超时）
# 用法：在 api-douyin-backend 已启动且数据库可连时执行
#   cd docs\self-test-screenshots
#   .\run-f02-f03-selftest.ps1

$ErrorActionPreference = 'Stop'
$BaseUrl = 'http://127.0.0.1:8080/api/v1'
$OutDir = $PSScriptRoot
$LoginBody = Join-Path $OutDir 'login-body.json'

[System.IO.File]::WriteAllText($LoginBody, '{"username":"douyin_creator","password":"password123"}')

function Read-JsonFile {
    param([string]$Path)
    $bytes = [System.IO.File]::ReadAllBytes($Path)
    $text = [System.Text.Encoding]::UTF8.GetString($bytes)
    return $text | ConvertFrom-Json
}

function Invoke-CurlJson {
    param([string]$Name, [string[]]$CurlArgs)
    $out = Join-Path $OutDir $Name
    & curl.exe -s -m 120 @CurlArgs -o $out -w "`nHTTP %{http_code}`n"
    Write-Host "==> $Name"
    Get-Content $out -Encoding UTF8
    return $out
}

Write-Host '=== 1. Login ===' -ForegroundColor Cyan
Invoke-CurlJson '01-login.json' @(
    '-X', 'POST', "$BaseUrl/auth/login",
    '-H', 'Content-Type: application/json',
    '--data-binary', "@$LoginBody"
) | Out-Null

$login = Read-JsonFile (Join-Path $OutDir '01-login.json')
if (-not $login.success) { throw "Login failed: $([System.IO.File]::ReadAllText((Join-Path $OutDir '01-login.json')))" }
$token = $login.token
$auth = "Authorization: Bearer $token"

Write-Host '=== 2. GET recommendations (before) ===' -ForegroundColor Cyan
Invoke-CurlJson '02-recommendations-before.json' @(
    "$BaseUrl/videos/recommendations",
    '-H', $auth
) | Out-Null
$rec1 = Read-JsonFile (Join-Path $OutDir '02-recommendations-before.json')
$firstId = $rec1.videos[0].id
Write-Host "videos count=$($rec1.videos.Count), first id=$firstId, likes=$($rec1.videos[0].likes_count)"

Write-Host '=== 3. POST view ===' -ForegroundColor Cyan
Invoke-CurlJson '03-post-view.json' @(
    '-X', 'POST', "$BaseUrl/videos/$firstId/views",
    '-H', $auth
) | Out-Null

Write-Host '=== 4. GET recommendations (after view) ===' -ForegroundColor Cyan
Invoke-CurlJson '04-recommendations-after.json' @(
    "$BaseUrl/videos/recommendations",
    '-H', $auth
) | Out-Null
$rec2 = Read-JsonFile (Join-Path $OutDir '04-recommendations-after.json')
$dup = $rec2.videos | Where-Object { $_.id -eq $firstId }
Write-Host "dedup ok=$($null -eq $dup), count=$($rec2.videos.Count)"

Write-Host '=== 5. DELETE reset views ===' -ForegroundColor Cyan
Invoke-CurlJson '05-reset-views.json' @(
    '-X', 'DELETE', "$BaseUrl/users/me/views",
    '-H', $auth
) | Out-Null

Write-Host '=== 6. GET recommendations (after reset) ===' -ForegroundColor Cyan
Invoke-CurlJson '06-recommendations-after-reset.json' @(
    "$BaseUrl/videos/recommendations",
    '-H', $auth
) | Out-Null

$summary = [ordered]@{
    testedAt       = (Get-Date -Format 'yyyy-MM-dd HH:mm:ss')
    loginSuccess   = $true
    beforeCount    = $rec1.videos.Count
    dedupAfterView = ($null -eq $dup)
    afterViewCount = $rec2.videos.Count
}
$summary | ConvertTo-Json | Set-Content (Join-Path $OutDir '00-summary.json') -Encoding UTF8
Write-Host '=== Done. See 00-summary.json ===' -ForegroundColor Green
$summary | Format-List
