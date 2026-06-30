#requires -Version 5
<#
.SYNOPSIS
    Build the self-contained Windows artifacts for eventb-checker.

.DESCRIPTION
    Produces two artifacts under dist\ from the Gradle shadow (fat) jar
    (<arch> is the -Arch value, x64 or arm64):

      * eventb-checker-<version>-windows-<arch>.zip  - portable app-image with a
        bundled, jlink-trimmed JRE 21 (no system Java required). Built by jpackage.

      * eventb-checker-<version>-windows-<arch>.msi  - installer that drops the same
        app-image into C:\Program Files\eventb-checker and appends that directory
        to the system PATH. Built with WiX 3 (heat/candle/light) from the
        app-image plus packaging\windows\Product.wxs.

    When run under GitHub Actions, the produced zip/msi paths are written to
    $GITHUB_OUTPUT as `zip` and `msi`.

    Requirements (must be on PATH, or WiX discoverable via %WIX%):
      * JDK 21 (jpackage + jlink)
      * WiX Toolset 3.x (heat.exe, candle.exe, light.exe)

    Used by .github\workflows\release.yml and reproducible on a Windows dev box:
        .\gradlew.bat shadowJar
        pwsh packaging\windows\package.ps1 -Version 1.8

.PARAMETER Version
    Release version without the leading 'v' (e.g. 1.8). Defaults to the version
    embedded in the eventb-checker-*-all.jar filename under -JarDir.

.PARAMETER Arch
    Architecture label for the MSI/ZIP: 'x64' or 'arm64'. Defaults to the host
    architecture. jpackage always bundles a JRE matching the JDK/OS it runs on,
    so this only labels the output and sets the WiX target -- build on a host
    whose architecture matches.
#>
[CmdletBinding()]
param(
    [string]$Version,
    [string]$Arch,
    [string]$JarDir = "build\libs",
    [string]$OutDir = "dist"
)

$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest

# packaging\windows\ -> repo root
$repoRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
Set-Location $repoRoot

$appName = "eventb-checker"
$mainClass = "com.eventb.checker.MainKt"

# --- resolve target architecture -------------------------------------------
# PROCESSOR_ARCHITEW6432 holds the native arch under WOW64 emulation (e.g. an x64
# PowerShell on an ARM64 host), where PROCESSOR_ARCHITECTURE would misreport AMD64.
$hostArch = switch (@($env:PROCESSOR_ARCHITEW6432, $env:PROCESSOR_ARCHITECTURE | Where-Object { $_ })[0]) {
    'ARM64' { 'arm64' }
    'AMD64' { 'x64' }
    default { 'x64' }
}
if (-not $Arch) { $Arch = $hostArch }
elseif ($Arch -notin 'x64', 'arm64') { throw "Invalid -Arch '$Arch'; expected x64 or arm64." }
# jpackage always bundles a JRE matching the host, so a non-host -Arch mislabels the output.
if ($hostArch -ne $Arch) {
    Write-Warning "Requested -Arch $Arch but this host is $hostArch; the bundled JRE will be $hostArch and will not match the $Arch MSI."
}

# --- locate the shadow (fat) jar and resolve the version --------------------
# Select by version (not by file recency) so a stale jar in build\libs can't be
# packaged and mislabeled as the wrong version.
$jars = @(Get-ChildItem -Path $JarDir -Filter "eventb-checker-*-all.jar" -ErrorAction SilentlyContinue)
if (-not $jars) {
    throw "No eventb-checker-*-all.jar found in '$JarDir'. Run './gradlew shadowJar' first."
}
if ($Version) {
    $jar = $jars | Where-Object { $_.Name -eq "eventb-checker-$Version-all.jar" } | Select-Object -First 1
    if (-not $jar) { throw "No 'eventb-checker-$Version-all.jar' in '$JarDir' (found: $($jars.Name -join ', '))." }
} elseif ($jars.Count -eq 1) {
    $jar = $jars[0]
    if ($jar.Name -match '^eventb-checker-(.+)-all\.jar$') { $Version = $Matches[1] }
    else { throw "Could not derive a version from '$($jar.Name)'; pass -Version explicitly." }
} else {
    throw "Multiple jars in '$JarDir' ($($jars.Name -join ', ')); pass -Version to disambiguate."
}
Write-Host "Packaging $appName $Version  ($Arch)  (jar: $($jar.Name))"

$inputDir = "build\jpackage-input"
$wixWork  = "build\wix"
$imageDir = Join-Path $OutDir "app-image"
$msiDir   = Join-Path $OutDir "msi"
$appImage = Join-Path $imageDir $appName            # dist\app-image\eventb-checker
$zipPath  = Join-Path $OutDir "$appName-$Version-windows-$Arch.zip"
$msiPath  = Join-Path $msiDir  "$appName-$Version-windows-$Arch.msi"

# --- clean & stage an input dir holding ONLY the fat jar --------------------
# build\libs also contains the thin jar (and possibly stale versions); jpackage
# copies the whole --input dir into the image, so it must contain just the fat jar.
Remove-Item -Recurse -Force $inputDir, $imageDir, $wixWork -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Force -Path $inputDir, $msiDir, $wixWork | Out-Null
Copy-Item $jar.FullName (Join-Path $inputDir $jar.Name)

# --- 1) jpackage app-image (launcher + bundled JRE) ------------------------
$jlinkOpts = "--strip-debug --no-man-pages --no-header-files --strip-native-commands"
Write-Host "==> jpackage --type app-image"
& jpackage `
    --type app-image `
    --name $appName `
    --app-version $Version `
    --input $inputDir `
    --main-jar $jar.Name `
    --main-class $mainClass `
    --vendor "eventb-rossi" `
    --description "Event-B model checker" `
    --win-console `
    --jlink-options $jlinkOpts `
    --dest $imageDir
if ($LASTEXITCODE -ne 0) { throw "jpackage app-image failed ($LASTEXITCODE)" }

$launcher = Join-Path $appImage "$appName.exe"
if (-not (Test-Path $launcher)) { throw "Expected launcher not found: $launcher" }

# --- 2) portable ZIP --------------------------------------------------------
# ZipFile.CreateFromDirectory is far faster than Compress-Archive for the
# many-file JRE tree; includeBaseDirectory=$true keeps the eventb-checker\ top
# level. It needs absolute paths (its CWD differs from PowerShell's location)
# and a non-existent destination.
Write-Host "==> portable ZIP -> $zipPath"
Remove-Item -Force $zipPath -ErrorAction SilentlyContinue
# Windows PowerShell 5.1 needs this assembly loaded; PowerShell 7 already has the
# type and has no such assembly name, so ignore the load failure there.
try { Add-Type -AssemblyName System.IO.Compression.FileSystem } catch { }
$appImageFull = (Resolve-Path -LiteralPath $appImage).Path
$zipFull = [System.IO.Path]::GetFullPath((Join-Path (Get-Location) $zipPath))
[System.IO.Compression.ZipFile]::CreateFromDirectory(
    $appImageFull, $zipFull, [System.IO.Compression.CompressionLevel]::Optimal, $true)

# --- 3) MSI via WiX 3 -------------------------------------------------------
function Resolve-WixTool([string]$name) {
    $cmd = Get-Command $name -ErrorAction SilentlyContinue
    if ($cmd) { return $cmd.Source }
    if ($env:WIX) {
        $candidate = Join-Path $env:WIX "bin\$name"
        if (Test-Path $candidate) { return $candidate }
    }
    throw "WiX 3 tool '$name' not found on PATH or under %WIX%. Install WiX Toolset 3.x."
}
$heat   = Resolve-WixTool "heat.exe"
$candle = Resolve-WixTool "candle.exe"
$light  = Resolve-WixTool "light.exe"

$harvest = Join-Path $wixWork "harvest.wxs"
Write-Host "==> heat (harvest app-image into ComponentGroup AppFiles)"
# -ag: derive stable component GUIDs from each keypath (deterministic across
# builds) rather than -gg's fresh random GUIDs, which break installer repair.
& $heat dir $appImage -nologo -cg AppFiles -dr INSTALLDIR -ag -srd -sfrag -scom -sreg `
    -var var.AppDir -out $harvest
if ($LASTEXITCODE -ne 0) { throw "heat failed ($LASTEXITCODE)" }

Write-Host "==> candle"
& $candle -nologo -arch $Arch "-dProductVersion=$Version" "-dPlatform=$Arch" "-dAppDir=$appImage" -out "$wixWork\" `
    "packaging\windows\Product.wxs" $harvest
if ($LASTEXITCODE -ne 0) { throw "candle failed ($LASTEXITCODE)" }

Write-Host "==> light -> $msiPath"
# -sval skips ICE validation (needs COM/admin, flaky on CI); -spdb skips the .wixpdb.
& $light -nologo -sval -spdb -out $msiPath "$wixWork\Product.wixobj" "$wixWork\harvest.wixobj"
if ($LASTEXITCODE -ne 0) { throw "light failed ($LASTEXITCODE)" }

# Single source of truth for the artifact paths: hand them to the workflow.
if ($env:GITHUB_OUTPUT) {
    @(
        "zip=$((Resolve-Path -LiteralPath $zipPath).Path)"
        "msi=$((Resolve-Path -LiteralPath $msiPath).Path)"
    ) | Out-File -FilePath $env:GITHUB_OUTPUT -Append -Encoding ascii
}

Write-Host ""
Write-Host "Done. Artifacts:"
Write-Host "  ZIP: $zipPath"
Write-Host "  MSI: $msiPath"
