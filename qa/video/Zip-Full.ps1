param([switch]$VerifyOnly)
$ErrorActionPreference='Stop'
$workspacePath=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..\..'))
$projectPath=[IO.Path]::GetFullPath((Join-Path $workspacePath 'video-production\full'))
$archivePath=Join-Path $workspacePath 'video-production\VJCE-完整版-OpenFilm工程.zip'
$taskNode='E:\nodejs\node.exe'
if (-not $VerifyOnly) {
    & $taskNode (Join-Path $PSScriptRoot 'Package-Full.mjs')
    if ($LASTEXITCODE -ne 0) { throw 'Manifest generation failed' }
    $files=@(Get-Content -LiteralPath (Join-Path $projectPath 'delivery-files.json') -Raw | ConvertFrom-Json)
    $files+=@('delivery-files.json','sha256-files.json','VJCE-B站封面.png','VJCE-中文字幕.srt','VJCE-中文字幕.vtt')
    if(Test-Path -LiteralPath $archivePath){throw 'Preserve existing delivery archive; use a new name.'}
    $zip=[IO.Compression.ZipFile]::Open($archivePath,[IO.Compression.ZipArchiveMode]::Create)
    try {
        foreach($relativeFile in ($files | Select-Object -Unique)){
            $sourceFile=[IO.Path]::GetFullPath((Join-Path $projectPath $relativeFile))
            if (-not $sourceFile.StartsWith($projectPath+[IO.Path]::DirectorySeparatorChar,[StringComparison]::OrdinalIgnoreCase)) {throw 'Outside project'}
            if (-not (Test-Path -LiteralPath $sourceFile -PathType Leaf)) {throw "Missing $relativeFile"}
            [IO.Compression.ZipFileExtensions]::CreateEntryFromFile($zip,$sourceFile,('VJCE-完整版-OpenFilm工程/'+$relativeFile.Replace('\','/')),[IO.Compression.CompressionLevel]::Optimal) | Out-Null
        }
    } finally {$zip.Dispose()}
}
$verified=0
$checkZip=[IO.Compression.ZipFile]::OpenRead($archivePath)
try {
    $expected=Get-Content -LiteralPath (Join-Path $projectPath 'sha256-files.json') -Raw | ConvertFrom-Json -AsHashtable
    foreach($entry in $checkZip.Entries){
        if($entry.FullName -match '(^|/)\.film(/|$)|(^|/)\.\./|^/'){throw 'Unexpected archive path'}
        $relative=$entry.FullName.Substring('VJCE-完整版-OpenFilm工程/'.Length)
        if($expected.ContainsKey($relative)){
            $stream=$entry.Open()
            try {$hash=[Convert]::ToHexString([Security.Cryptography.SHA256]::HashData($stream)).ToLowerInvariant()} finally {$stream.Dispose()}
            if($hash -ne $expected[$relative]){throw "ZIP hash mismatch: $relative"}
            $verified++
        }
    }
    if($verified -ne $expected.Count){throw 'Missing ZIP entries'}
    [PSCustomObject]@{Archive=$archivePath;Entries=$checkZip.Entries.Count;VerifiedHashes=$verified;Bytes=(Get-Item -LiteralPath $archivePath).Length} | ConvertTo-Json
} finally {$checkZip.Dispose()}
