#Requires -Version 5.1
<#
  UnturnedAssistant V4.0 构建脚本
  用法:
    .\build.ps1              # 完整构建: javac(Java 27) + jar + jpackage app-image + 7z SFX 单exe
    .\build.ps1 -SkipSfx     # 只到 app-image 文件夹
  产物:
    dist\UnturnedAssistant\UnturnedAssistant.exe   (自带运行时的应用目录)
    dist\UnturnedAssistant-<版本>.exe              (单文件绿色版: 双击直接运行)
#>
[CmdletBinding()]
param(
    [string]$Version = "4.0",
    [switch]$SkipSfx
)

$ErrorActionPreference = 'Stop'
$Jdk27 = 'C:\Program Files\Java\jdk-27'
if (-not (Test-Path (Join-Path $Jdk27 'bin\javac.exe'))) { throw "未找到 JDK 27: $Jdk27" }

$Root    = $PSScriptRoot
$Out     = Join-Path $Root 'out'
$Dist    = Join-Path $Root 'dist'
$Classes = Join-Path $Out 'classes'
$Stage   = Join-Path $Out 'jpackage-input'
$AppDir  = Join-Path $Dist 'UnturnedAssistant'

Write-Host '== 1/5 清理输出目录 =='
Remove-Item $Out, $Dist -Recurse -Force -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Force -Path $Classes, $Stage | Out-Null

Write-Host '== 2/5 javac (Java 27) =='
$sources = Get-ChildItem -Path (Join-Path $Root 'src') -Filter '*.java' -Recurse | ForEach-Object { $_.FullName }
& (Join-Path $Jdk27 'bin\javac.exe') --release 27 -encoding UTF-8 -d $Classes @sources
if ($LASTEXITCODE -ne 0) { throw 'javac 编译失败' }

Write-Host '== 3/5 资源与 jar =='
Copy-Item -Path (Join-Path $Root 'Resources\*') -Destination $Classes -Recurse -Force
$JarPath = Join-Path $Out 'UnturnedAssistant.jar'
& (Join-Path $Jdk27 'bin\jar.exe') --create --file $JarPath --manifest (Join-Path $Root 'src\META-INF\MANIFEST.MF') -C $Classes '.'
if ($LASTEXITCODE -ne 0) { throw 'jar 打包失败' }
Copy-Item $JarPath $Stage

Write-Host '== 4/5 jpackage app-image =='
& (Join-Path $Jdk27 'bin\jpackage.exe') --type app-image `
    --input $Stage --main-jar 'UnturnedAssistant.jar' --main-class 'Start' `
    --name 'UnturnedAssistant' --app-version $Version --vendor 'CatzTimes' `
    --dest $Dist
if ($LASTEXITCODE -ne 0) { throw 'jpackage 失败' }
Write-Host "app-image 完成: $AppDir"

if ($SkipSfx) { return }

Write-Host '== 5/5 7z SFX 封装单 exe =='
$ToolsDir = Join-Path $Root 'tools\7zip'
New-Item -ItemType Directory -Force -Path $ToolsDir | Out-Null

# 打包器: 本机安装的 7-Zip, 或 tools 下免安装的 7zr.exe
$sevenZip = $null
foreach ($cand in @("$env:ProgramFiles\7-Zip\7z.exe", "${env:ProgramFiles(x86)}\7-Zip\7z.exe", (Join-Path $ToolsDir '7zr.exe'))) {
    if (Test-Path $cand) { $sevenZip = $cand; break }
}
if (-not $sevenZip) {
    Write-Host '本机无 7-Zip, 下载官方独立版 7zr.exe ...'
    Invoke-WebRequest -Uri 'https://www.7-zip.org/a/7zr.exe' -OutFile (Join-Path $ToolsDir '7zr.exe') -UseBasicParsing
    $sevenZip = Join-Path $ToolsDir '7zr.exe'
}

# SFX 模块: 优先 7zSD.sfx (7z Extra, 支持完全静默), 否则标准安装自带的 7zS.sfx
$sfxModule = $null
foreach ($cand in @((Join-Path $ToolsDir '7zSD.sfx'), "$env:ProgramFiles\7-Zip\7zS.sfx", "${env:ProgramFiles(x86)}\7-Zip\7zS.sfx")) {
    if (Test-Path $cand) { $sfxModule = $cand; break }
}
if (-not $sfxModule) {
    # 7zSD.sfx 来自官方 LZMA SDK 的 bin 目录（新版 7-Zip 安装包与 7z Extra 均已不再附带）
    Write-Host '下载官方 LZMA SDK (提取 7zSD.sfx 安装器模块) ...'
    $sdk7z = Join-Path $ToolsDir 'lzma-sdk.7z'
    $downloaded = $false
    foreach ($ver in @('lzma2604', 'lzma2601', 'lzma2501', 'lzma2301')) {
        try {
            Invoke-WebRequest -Uri "https://www.7-zip.org/a/$ver.7z" -OutFile $sdk7z -UseBasicParsing -ErrorAction Stop
            $downloaded = $true
            break
        } catch {
            Write-Host "  $ver.7z 不可用，尝试下一个版本..."
        }
    }
    if (-not $downloaded) { throw '无法下载 LZMA SDK（网络不可用或全部版本 404）' }
    & $sevenZip e $sdk7z ("-o" + $ToolsDir) 'bin\7zSD.sfx' -y | Out-Null
    if ($LASTEXITCODE -ne 0) { throw 'LZMA SDK 解压失败' }
    $sfxModule = Join-Path $ToolsDir '7zSD.sfx'
    if (-not (Test-Path $sfxModule)) { throw '未获得 SFX 模块' }
}

# 归档: dist 下整个 UnturnedAssistant 文件夹
$archive = Join-Path $Out 'sfx.7z'
Push-Location $Dist
& $sevenZip a -t7z -mx=9 -bd $archive 'UnturnedAssistant' | Out-Null
Pop-Location
if ($LASTEXITCODE -ne 0) { throw '7z 归档失败' }

# SFX 配置: 解压到临时目录 → 运行主程序 → 退出后自清理; 无任何向导
$configPath = Join-Path $Out 'sfx-config.txt'
$configText = @()
$configText += ';!@Install@!UTF-8!'
$configText += "Title=""UnturnedAssistant V$Version"""
$configText += 'ExtractDialogText="正在启动 UnturnedAssistant, 首次启动需解压, 请稍候..."'
$configText += 'RunProgram="UnturnedAssistant\UnturnedAssistant.exe"'
$configText += ';!@InstallEnd@!'
[IO.File]::WriteAllLines($configPath, $configText, (New-Object System.Text.UTF8Encoding($false)))

$singleExe = Join-Path $Dist ("UnturnedAssistant-$Version.exe")
$outStream = [IO.File]::Create($singleExe)
try {
    foreach ($part in @($sfxModule, $configPath, $archive)) {
        $bytes = [IO.File]::ReadAllBytes($part)
        $outStream.Write($bytes, 0, $bytes.Length)
    }
} finally {
    $outStream.Dispose()
}
Write-Host ("单文件 exe 完成: {0} ({1:N1} MB)" -f $singleExe, ((Get-Item $singleExe).Length / 1MB))
