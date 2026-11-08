param([string]$CaptureTake='', [string]$ShowcaseTake='', [string]$FilmDirectory='')
$ErrorActionPreference='Stop'
$workspacePath=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..\..'))
$takesDirectory=Join-Path $workspacePath 'build\video-takes'
$filmDirectory=if ($FilmDirectory) { [IO.Path]::GetFullPath($FilmDirectory) } else { Join-Path $workspacePath 'video-production\sample' }
function Resolve-Take([string]$phase,[string]$selected) {
    if ($selected) { $folder=Get-Item -LiteralPath $selected }
    else { $folder=Get-ChildItem -LiteralPath $takesDirectory -Directory -Filter "$phase-*" | Where-Object { Test-Path -LiteralPath (Join-Path $_.FullName 'take.json') } | Sort-Object Name -Descending | Select-Object -First 1 }
    if (-not $folder) { throw "No completed $phase take" }
    return (Get-Content -LiteralPath (Join-Path $folder.FullName 'take.json') -Raw | ConvertFrom-Json)
}
function Source-Seconds($take,[string]$marker) {
    $event=Get-Content -LiteralPath (Join-Path $take.markers ($marker+'.json')) -Raw | ConvertFrom-Json
    # Recorder starts about two seconds before its ready acknowledgement.
    return [math]::Round(($event.epochMillis-$take.recorderReadyEpochMillis)/1000+2,3)
}
function Media-Source($take) { return 'assets/video/'+[IO.Path]::GetFileName($take.media) }
function Number([double]$value) { return $value.ToString('0.###',[Globalization.CultureInfo]::InvariantCulture) }
$capture=Resolve-Take 'capture' $CaptureTake
$showcase=Resolve-Take 'showcase' $ShowcaseTake
$heroIn=Source-Seconds $showcase 'jei'
$worldIn=Source-Seconds $capture 'world'
$selectionIn=Source-Seconds $capture 'selection'
$previewIn=Source-Seconds $capture 'preview'
$captureSrc=Media-Source $capture
$heroSrc=Media-Source $showcase
$voice='assets/audio/vo/sample-hazel.wav'
if (-not (Test-Path -LiteralPath (Join-Path $filmDirectory $voice))) { throw 'Hazel narration is missing' }
$selectionEnd=$previewIn
$selectionSpeed=($selectionEnd-$selectionIn)/7.0
$selectionSpeedText=([math]::Ceiling($selectionSpeed*1e12)/1e12).ToString('0.############',[Globalization.CultureInfo]::InvariantCulture)
$filmPath=Join-Path $filmDirectory 'film.html'
# Read immediately before changing the document; never replace tracks edited in Studio.
if (Test-Path -LiteralPath $filmPath) {
    $previousFilm=Get-Content -LiteralPath $filmPath -Raw -Encoding UTF8
    if ($previousFilm -match '<section') { throw 'Film already exists. Generate into a new FilmDirectory to preserve Studio edits.' }
}
$film=@"
<!doctype html>
<html lang="zh-CN"><head><meta charset="utf-8"><meta name="viewport" content="width=1920, height=1080"></head><body>
<!-- VJCE_GENERATED_SAMPLE -->
<section>
  <iframe id="opening-title" src="opening.html#t=0,4" at="0"></iframe>
  <iframe id="world-title" src="world.html#t=0,4" at="4"></iframe>
  <iframe id="capture-title" src="capture.html#t=0,7" at="8"></iframe>
  <iframe id="preview-title" src="preview.html#t=0,4" at="15"></iframe>
  <iframe id="final-title" src="ending.html#t=0,6" at="19"></iframe>
</section>
<section>
  <video id="hero-open" src="${heroSrc}#t=$(Number $heroIn),$(Number ($heroIn+4))" at="0" style="width:2400px;height:1350px;left:-240px;top:-160px" muted></video>
  <video id="world" src="${captureSrc}#t=$(Number $worldIn),$(Number ($worldIn+4))" at="4" muted></video>
  <video id="world-capture" src="${captureSrc}#t=$(Number $selectionIn),$(Number $selectionEnd)" at="8" speed="$selectionSpeedText" muted></video>
  <video id="editor-preview" src="${captureSrc}#t=$(Number ($previewIn+1)),$(Number ($previewIn+5))" at="15" style="width:3072px;height:1728px;left:-300px;top:-220px" muted></video>
  <video id="normal-jei" src="${heroSrc}#t=$(Number ($heroIn+6)),$(Number ($heroIn+12))" at="19" style="width:2400px;height:1350px;left:-240px;top:-160px" muted></video>
</section>
<section><audio id="hazel-narration" src="$voice" at="0.5" speed="0.85" volume="1"></audio></section>
<section><audio id="original-bed" src="assets/audio/sample-bed.wav" volume="0.55"></audio></section>
</body></html>
"@
[IO.File]::WriteAllText($filmPath,$film,[Text.UTF8Encoding]::new($false))
$cutData=@{capture=$capture; showcase=$showcase; heroIn=$heroIn; worldIn=$worldIn; selectionIn=$selectionIn; selectionEnd=$selectionEnd; selectionSpeed=$selectionSpeed; previewIn=$previewIn}
[IO.File]::WriteAllText((Join-Path $filmDirectory 'cuts.json'),($cutData | ConvertTo-Json -Depth 6),[Text.UTF8Encoding]::new($false))
$pages = @(
    @('opening','把世界里的结构，放进 JEI','实际 JEI 页面'),
    @('world','从世界里搭建','真实游戏录屏'),
    @('capture','框选、导入、应用',"$(Number $selectionSpeed)× 加速"),
    @('preview','结构、材料与说明','编辑器预览'),
    @('ending','做成整合包自己的说明页','重新启动后 · 实际 JEI')
)
foreach ($page in $pages) {
    $markup = '<!doctype html><html lang="zh-CN"><head><meta charset="utf-8"><link rel="stylesheet" href="identity.css"></head><body><div class="name"><b>VJCE</b> JEI 分类可视化编辑器</div><div class="message">'+$page[1]+'</div><div class="note">'+$page[2]+'</div><script>window.film={width:1920,height:1080,frame(t){document.body.style.opacity=String(Math.min(1,Math.max(0,t/.22)));}};</script></body></html>'
    [IO.File]::WriteAllText((Join-Path $filmDirectory ($page[0]+'.html')),$markup,[Text.UTF8Encoding]::new($false))
}
Write-Output "25-second OpenFilm timeline written: $filmPath"
Write-Output "Source cuts use recorder-ready acknowledgement plus estimated 2s recorder lead-in. Verify cuts with openfilm look before render."
