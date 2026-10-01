param(
    [string]$VoiceJar = 'deps/plasmovoice-2.1.17.jar',
    [string]$Cache = "$env:USERPROFILE/.gradle/caches/modules-2/files-2.1",
    [string]$Jdk = 'C:/Program Files/Java/jdk-25.0.4'
)
$ErrorActionPreference = 'Stop'
Set-Location $PSScriptRoot
function Find-Jar([string]$Pattern) {
    $match = Get-ChildItem -LiteralPath $Cache -Recurse -File -Filter $Pattern | Select-Object -First 1
    if (!$match) { throw "Missing compile dependency $Pattern under $Cache" }
    return $match.FullName
}
$mc=Get-ChildItem "$env:USERPROFILE/.gradle/caches/fabric-loom/minecraftMaven/net/minecraft/minecraft-merged-intermediary" -Recurse -Filter '*.jar' | Select-Object -First 1
$deps = @($VoiceJar,$mc.FullName, (Find-Jar 'fabric-loader-*.jar'), (Find-Jar 'sponge-mixin-*.jar'),(Find-Jar 'asm-9*.jar'),(Find-Jar 'asm-tree-9*.jar'), (Find-Jar 'jna-5.17.0.jar'),(Find-Jar 'fabric-key-binding-api-v1-*.jar'),(Find-Jar 'fabric-lifecycle-events-v1-*.jar'),(Find-Jar 'fabric-networking-api-v1-*.jar'),(Find-Jar 'fabric-command-api-v2-*.jar'),(Find-Jar 'fabric-api-base-*.jar'),(Find-Jar 'brigadier-*.jar'),(Find-Jar 'jspecify-*.jar'),(Find-Jar 'datafixerupper-*.jar'),(Find-Jar 'guava-*.jar'),(Find-Jar 'fastutil-*.jar'))
New-Item -ItemType Directory -Force build/deps-1219-min | Out-Null
$localDeps = foreach($dep in $deps) { $target = Join-Path 'build/deps-1219-min' ([IO.Path]::GetFileName($dep)); Copy-Item -LiteralPath $dep -Destination $target -Force; (Resolve-Path $target).Path }
$classpath = 'build/deps-1219-min/*'
New-Item -ItemType Directory -Force build/v1219/classes,build/v1219/test-classes | Out-Null
$sources = @(Get-ChildItem src/main/java -Recurse -Filter '*.java' | ForEach-Object FullName)
$sources += @(Get-ChildItem vendor/tarsos-core-2.5/be -Recurse -Filter '*.java' | ForEach-Object FullName)
& "$Jdk/bin/javac.exe" --release 21 -encoding UTF-8 -proc:none -classpath $classpath -d build/v1219/classes @sources
if ($LASTEXITCODE -ne 0) { throw 'Java compile failed' }
Copy-Item src/main/resources/* build/v1219/classes/ -Recurse -Force
Copy-Item LICENSE build/v1219/classes/LICENSE -Force
Copy-Item LICENSE-MIT-legacy,THIRD-PARTY-NOTICES.md build/v1219/classes/ -Force
Copy-Item vendor/downloads/LGPL-2.0.txt build/v1219/classes/ -Force
& "$Jdk/bin/jar.exe" --create --file build/plasmo-system-music-1.2.19+mc1.21.11-pv2.x.jar -C build/v1219/classes .
if ($LASTEXITCODE -ne 0) { throw 'Jar packaging failed' }
$tests = @(Get-ChildItem src/test/java -Recurse -Filter '*.java' | ForEach-Object FullName)
if($tests.Count -gt 0) {
    # Compile tests together with source: javac 25 on this host fails to resolve
    # package-private classes from the output directory in a separate invocation.
    & "$Jdk/bin/javac.exe" --release 21 -encoding UTF-8 -proc:none -classpath $classpath -d build/v1219/test-classes @sources @tests
    if($LASTEXITCODE -ne 0){throw 'Test compile failed'}
    & "$Jdk/bin/java.exe" --enable-native-access=ALL-UNNAMED -ea -cp "build/v1219/classes;build/v1219/test-classes;$classpath" local.plasmomusic.AudioTest
    if($LASTEXITCODE -ne 0){throw 'Audio tests failed'}
    & "$Jdk/bin/java.exe" -cp "build/v1219/classes;build/v1219/test-classes;$classpath" local.plasmomusic.AudioActivityTest
    if($LASTEXITCODE -ne 0){throw 'Audio activity tests failed'}
    & "$Jdk/bin/java.exe" -cp "build/v1219/classes;build/v1219/test-classes" local.plasmomusic.MusicEffectsTest
    if($LASTEXITCODE -ne 0){throw 'Music effects tests failed'}
    & "$Jdk/bin/java.exe" -cp "build/v1219/classes;build/v1219/test-classes" local.plasmomusic.AnimationLevelTest
    if($LASTEXITCODE -ne 0){throw 'Talking Heads animation tests failed'}
    & "$Jdk/bin/java.exe" -cp "build/v1219/classes;build/v1219/test-classes;$classpath" local.plasmomusic.VoiceCensorTest
    if($LASTEXITCODE -ne 0){throw 'Voice censor tests failed'}
    & "$Jdk/bin/java.exe" -cp "build/v1219/classes;build/v1219/test-classes;$classpath" local.plasmomusic.StudioEffectsTest
    if($LASTEXITCODE -ne 0){throw 'Studio effects tests failed'}
    & "$Jdk/bin/java.exe" -cp "build/v1219/classes;build/v1219/test-classes;$classpath" local.plasmomusic.DspPanelTest
    if($LASTEXITCODE -ne 0){throw 'DSP panel tests failed'}
    & "$Jdk/bin/java.exe" -cp "build/v1219/classes;build/v1219/test-classes;$classpath" local.plasmomusic.VoicePresetTest
    if($LASTEXITCODE -ne 0){throw 'Voice preset level tests failed'}
    & "$Jdk/bin/java.exe" -cp "build/v1219/classes;build/v1219/test-classes;$classpath" local.plasmomusic.CompatibilityTest deps/plasmovoice-2.1.17.jar deps/plasmovoice-2.2.0-beta.1.jar
    if($LASTEXITCODE -ne 0){throw 'Compatibility tests failed'}
    & "$Jdk/bin/java.exe" -cp "build/v1219/classes;build/v1219/test-classes;$classpath" local.plasmomusic.ProcessSelectionTest
    if($LASTEXITCODE -ne 0){throw 'Process selection tests failed'}
    & "$Jdk/bin/java.exe" -cp "build/v1219/classes;build/v1219/test-classes;$classpath" local.plasmomusic.ProcessTargetTrackerTest
    if($LASTEXITCODE -ne 0){throw 'Process target recovery tests failed'}
}
Write-Output 'BUILD OK: build/plasmo-system-music-1.2.19+mc1.21.11-pv2.x.jar'
