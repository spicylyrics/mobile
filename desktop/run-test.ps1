# Scripted GSMTC run against one player: plays, pauses, resumes and seeks it through the helper,
# with the console app logging every raw report. Usage: .\desktop\run-test.ps1 spotify
param([Parameter(Mandatory)][string]$App, [string]$Name = $App)
$root = Split-Path $PSScriptRoot
$gsmtc = "$PSScriptRoot\gsmtc\target\release\gsmtc.exe"
$logs = New-Item -ItemType Directory -Force "$PSScriptRoot\logs"
$p = Start-Process "$PSScriptRoot\build\install\desktop\bin\desktop.bat" -ArgumentList "--app", $App, "--log", "`"$logs\$Name.jsonl`"" `
    -WorkingDirectory $root -NoNewWindow -RedirectStandardOutput "$logs\$Name.txt" -RedirectStandardError "$logs\$Name.err.txt" -PassThru
function ctl { & $gsmtc ctl $App @args | Add-Content "$logs\$Name.ctl.jsonl" }
ctl play;      Start-Sleep 40
ctl pause;     Start-Sleep 5
ctl play;      Start-Sleep 25
ctl seek 90000; Start-Sleep 25
ctl seek 30000; Start-Sleep 20
ctl pause;     Start-Sleep 1
ctl play;      Start-Sleep 20
Get-CimInstance Win32_Process | Where-Object { $_.CommandLine -match "NowPlayingKt|gsmtc.exe" } | ForEach-Object { Stop-Process -Id $_.ProcessId -Force }
