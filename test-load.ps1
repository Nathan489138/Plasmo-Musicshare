param([switch]$SettingsOnly,[switch]$WithTalkingHeads,[switch]$ExpectDisabled,[string]$VoiceJar='deps/plasmovoice-2.1.17.jar',[string]$TalkingHeadsJar='deps/talking-heads-1.1.3+1.21.11+fabric.jar',[string]$Jdk='C:/Program Files/Java/jdk-25.0.4',[string]$RunName='default')
$ErrorActionPreference='Stop'
Set-Location $PSScriptRoot
$cache="$env:USERPROFILE/.gradle/caches/modules-2/files-2.1"
if($RunName -notmatch '^[a-zA-Z0-9-]+$'){throw 'Invalid RunName'}
$run=Join-Path $PSScriptRoot $(if($WithTalkingHeads){"build/load-test-1215-heads-$RunName"}else{"build/load-test-1215-min-$RunName"})
New-Item -ItemType Directory -Force "$run/mods","$run/libs" | Out-Null
$libs=Get-ChildItem $cache -Recurse -Filter '*.jar' | Where-Object { $_.Name -notmatch '^(fabric-(?!loader)|yarn|intermediary|mercury|sponge|jna-5.14|jna-platform-5.14)' -and $_.Name -notmatch 'natives-(windows-x86|windows-arm64|osx|linux)' }
foreach($lib in $libs){Copy-Item -LiteralPath $lib.FullName -Destination "$run/libs/$($lib.Name)" -Force}
Get-ChildItem $cache -Recurse -Filter 'sponge-mixin-*.jar' | ForEach-Object {Copy-Item -LiteralPath $_.FullName -Destination "$run/libs/$($_.Name)" -Force}
Get-ChildItem $cache -Recurse -Filter 'intermediary-1.21.11-v2.jar' | ForEach-Object {Copy-Item -LiteralPath $_.FullName -Destination "$run/libs/$($_.Name)" -Force}
$mc=Get-ChildItem "$env:USERPROFILE/.gradle/caches/fabric-loom/minecraftMaven/net/minecraft/minecraft-merged-intermediary" -Recurse -Filter '*.jar' | Select-Object -First 1
Copy-Item -LiteralPath $mc.FullName -Destination "$run/libs/minecraft.jar" -Force
Get-ChildItem $cache -Recurse -Filter 'fabric-api-0.*.jar' | ForEach-Object {Copy-Item -LiteralPath $_.FullName -Destination "$run/mods/$($_.Name)" -Force}
Copy-Item -LiteralPath $VoiceJar -Destination "$run/mods/plasmovoice.jar" -Force
$old=Join-Path $run 'mods/plasmo-system-music-1.0.0+mc1.21.11-pv2.1.16.jar'
if(Test-Path -LiteralPath $old){Rename-Item -LiteralPath $old -NewName 'plasmo-system-music-1.0.0.jar.disabled' -Force}
Copy-Item build/plasmo-system-music-1.2.15+mc1.21.11-pv2.x.jar "$run/mods/" -Force
if($WithTalkingHeads){Copy-Item -LiteralPath $TalkingHeadsJar -Destination "$run/mods/" -Force}
$cp="$PSScriptRoot/build/v1215/test-classes;$run/libs/*"
$mode=if($ExpectDisabled){'expect-disabled'}elseif($SettingsOnly){'settings-only'}else{'all'}
& "$jdk/bin/java.exe" --enable-native-access=ALL-UNNAMED -cp $cp local.plasmomusic.LoadTest $run "$PSScriptRoot/build/v1215/test-classes" $mode
if($LASTEXITCODE -ne 0){throw 'Fabric class load test failed'}
