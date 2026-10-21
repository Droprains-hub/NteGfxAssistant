# 异环画质助手 · 正式发布构建
# 用法： powershell -ExecutionPolicy Bypass -File tools\build-release.ps1
# 依赖：JDK 17+、Android SDK（local.properties 指定）、gradle 8.13

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$apk = Join-Path $root 'app\build\outputs\apk\release\app-release.apk'
$outDir = Join-Path $root '..\output\20261019-yhqh-v2\build'

if (-not $env:JAVA_HOME) {
    $env:JAVA_HOME = 'C:\Program Files\Java\jdk-21.0.12'
}
$env:Path = "$env:JAVA_HOME\bin;$env:Path"

# 1. 构建
Write-Host '[1/4] 构建 release...' -ForegroundColor Cyan
Push-Location $root
& gradle :app:assembleRelease --console=plain
if ($LASTEXITCODE -ne 0) { Pop-Location; throw '构建失败' }
Pop-Location

if (-not (Test-Path $apk)) { throw "未找到产物：$apk" }

# 2. 校验签名证书
Write-Host '[2/4] 校验签名证书...' -ForegroundColor Cyan
$sdk = (Get-Content (Join-Path $root 'local.properties') | Where-Object { $_ -match '^sdk.dir=' }) -replace '^sdk.dir=', ''
$sdk = $sdk -replace '\\\\', '\' -replace '\\:', ':'
$apksigner = Join-Path $sdk 'build-tools\36.0.0\apksigner.bat'
$certs = & $apksigner verify --print-certs $apk 2>&1 | Out-String

$expectedFile = Join-Path $PSScriptRoot 'release-signing.sha256'
$expected = (Select-String -Path $expectedFile -Pattern '^cert_sha256=').Line -replace '^cert_sha256=', ''
$actual = ([regex]::Match($certs, 'SHA-256 digest:\s*([0-9a-f]+)')).Groups[1].Value.ToUpper()
if ($actual -ne $expected.ToUpper()) {
    throw "签名不匹配！期望 $expected，实际 $actual"
}
Write-Host "  证书 SHA-256: $actual" -ForegroundColor Green

# 3. 计算摘要
Write-Host '[3/4] 计算 APK 摘要...' -ForegroundColor Cyan
$hash = (Get-FileHash -Algorithm SHA256 $apk).Hash
$size = (Get-Item $apk).Length
Write-Host "  SHA-256: $hash"
Write-Host "  大小: $size 字节"

# 4. 归档
Write-Host '[4/4] 归档到交付目录...' -ForegroundColor Cyan
if (-not (Test-Path $outDir)) { New-Item -ItemType Directory -Path $outDir | Out-Null }
$target = Join-Path $outDir 'NteGfxAssistant-v1.0.0-release.apk'
Copy-Item $apk $target -Force
Write-Host "  已输出：$target" -ForegroundColor Green
Write-Host '构建完成。' -ForegroundColor Green
