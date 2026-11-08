param(
    [ValidateSet('capture', 'showcase','world','showcase-full','authoring','native','resources','snapshot','create','publish')][string]$Phase = 'capture',
    [string]$Ffmpeg = 'E:\ffmpeg-master-latest-win64-gpl-shared\bin\ffmpeg.exe',
    [string]$Gradle = '',
    [string]$MediaDirectory = '',
    [string]$CreateJar = ''
)
$ErrorActionPreference = 'Stop'
$workspacePath = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..\..'))
$takeId = $Phase + '-' + (Get-Date -Format 'yyyyMMdd-HHmmss')
$takeDirectory = Join-Path $workspacePath ('build\video-takes\' + $takeId)
$mediaDirectory = if ($MediaDirectory) { [IO.Path]::GetFullPath($MediaDirectory) } else { Join-Path $workspacePath 'video-production\sample\assets\video' }
$gameDirectory = Join-Path $workspacePath 'video-production\game'
New-Item -ItemType Directory -Force -Path $takeDirectory,$mediaDirectory,$gameDirectory | Out-Null
if (-not (Test-Path -LiteralPath $Ffmpeg)) { throw "FFmpeg not found: $Ffmpeg" }
if (-not $Gradle) {
    $wrapperDirectory = Join-Path $env:USERPROFILE '.gradle\wrapper\dists\gradle-8.14.3-bin'
    if (Test-Path -LiteralPath $wrapperDirectory) {
        foreach ($distributionDirectory in (Get-ChildItem -LiteralPath $wrapperDirectory -Directory)) {
            $candidate = Join-Path $distributionDirectory.FullName 'gradle-8.14.3\bin\gradle.bat'
            if (Test-Path -LiteralPath $candidate) { $Gradle = $candidate; break }
        }
    }
    if (-not $Gradle) { $Gradle = Join-Path $workspacePath 'gradlew.bat' }
}
# A separate filming instance owns these options; normal run/ and player saves are untouched.
$optionsPath = Join-Path $gameDirectory 'options.txt'
if (-not (Test-Path -LiteralPath $optionsPath)) {
    [IO.File]::WriteAllText($optionsPath, "lang:zh_cn`nguiScale:2`nmaxFps:60`nrenderDistance:8`nsimulationDistance:5`npauseOnLostFocus:false`nshowSubtitles:false`n", [Text.UTF8Encoding]::new($false))
}
$gameArguments = @('runClient', "-PvideoDemo=$Phase", "-PvideoOut=$takeDirectory", '-PwithMekanism=true', '-Pneo_version=21.1.262', '--console=plain', '-I', (Join-Path $PSScriptRoot 'repositories.gradle'))
if ($CreateJar) { $gameArguments += @('-PwithCreate=true',"-PvideoCreateJar=$CreateJar") }
$gameLog = Join-Path $takeDirectory 'gradle.log'
$gameErrorLog = Join-Path $takeDirectory 'gradle-error.log'
$gameProcess = Start-Process -FilePath $Gradle -ArgumentList $gameArguments -WorkingDirectory $workspacePath -WindowStyle Hidden -RedirectStandardOutput $gameLog -RedirectStandardError $gameErrorLog -PassThru
Write-Output "Take: $takeId; starting development client. Log: $gameLog"
$recorder = $null
try {
    $readyPath = Join-Path $takeDirectory 'ready.json'
    $deadline = [DateTime]::UtcNow.AddMinutes(12)
    while (-not (Test-Path -LiteralPath $readyPath)) {
        $gameProcess.Refresh()
        if ($gameProcess.HasExited) { throw "Client exited before ready. See $gameLog and $gameErrorLog" }
        if ([DateTime]::UtcNow -gt $deadline) { throw "Client did not become ready. See $gameLog" }
        Start-Sleep -Milliseconds 400
    }
    $gameWindows = @(Get-Process -Name java,javaw -ErrorAction SilentlyContinue | Where-Object { $_.MainWindowTitle -like '*Minecraft*1.21.1*' -and $_.MainWindowHandle -ne 0 })
    if ($gameWindows.Count -ne 1) { throw "Expected one Minecraft 1.21.1 window, found $($gameWindows.Count); refusing to capture another window." }
    $windowHandle = [uint64]$gameWindows[0].MainWindowHandle.ToInt64()
    $outputPath = Join-Path $mediaDirectory ($takeId + '.mp4')
    $processInfo = [Diagnostics.ProcessStartInfo]::new($Ffmpeg)
    $processInfo.UseShellExecute = $false
    $processInfo.CreateNoWindow = $true
    $processInfo.RedirectStandardInput = $true
    $processInfo.RedirectStandardError = $true
    $recorderArguments = @('-hide_banner', '-n', '-filter_complex', "gfxcapture=hwnd=${windowHandle}:max_framerate=60:capture_cursor=0,hwdownload,format=bgra,format=yuv420p[v]", '-map', '[v]', '-an', '-c:v', 'libx264', '-preset', 'veryfast', '-crf', '18', '-fps_mode', 'cfr', '-r', '60', '-t', '180', '-movflags', '+faststart', $outputPath)
    foreach ($recorderArgument in $recorderArguments) { $processInfo.ArgumentList.Add($recorderArgument) }
    $recorder = [Diagnostics.Process]::Start($processInfo)
    $recorderError = $recorder.StandardError.ReadToEndAsync()
    Start-Sleep -Seconds 2
    if ($recorder.HasExited) { throw ('Recorder failed: ' + $recorderError.GetAwaiter().GetResult()) }
    $started = [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()
    [IO.File]::WriteAllText((Join-Path $takeDirectory 'recorder-ready'), [string]$started)
    $deadline = [DateTime]::UtcNow.AddMinutes(4)
    while (-not (Test-Path -LiteralPath (Join-Path $takeDirectory 'done.json'))) {
        $gameProcess.Refresh()
        if ($recorder.HasExited) { throw 'Recorder stopped before scene completion.' }
        if ($gameProcess.HasExited) { throw "Client exited during take. See $gameLog" }
        if ([DateTime]::UtcNow -gt $deadline) { throw "Take timed out. See $gameLog" }
        Start-Sleep -Milliseconds 250
    }
    $recorder.StandardInput.WriteLine('q')
    if (-not $recorder.WaitForExit(15000)) { throw 'Recorder did not close its MP4.' }
    $errorText = $recorderError.GetAwaiter().GetResult()
    [IO.File]::WriteAllText((Join-Path $takeDirectory 'ffmpeg.log'), $errorText)
    if ($recorder.ExitCode -ne 0) { throw "FFmpeg exit code $($recorder.ExitCode)." }
    $receipt = @{ phase=$Phase; take=$takeId; media=$outputPath; markers=$takeDirectory; recorderReadyEpochMillis=$started; gameProcessId=$gameWindows[0].Id }
    $receipt | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $takeDirectory 'take.json') -Encoding utf8
    if (-not $gameProcess.WaitForExit(60000)) { throw "Client did not exit after take; inspect $gameLog" }
    if ($gameProcess.ExitCode -ne 0) { throw "Client/Gradle failed after recording; see $gameLog" }
    Write-Output ($receipt | ConvertTo-Json -Compress)
} finally {
    if ($recorder -and -not $recorder.HasExited) {
        try { $recorder.StandardInput.WriteLine('q'); $null = $recorder.WaitForExit(10000) } catch { }
    }
}
