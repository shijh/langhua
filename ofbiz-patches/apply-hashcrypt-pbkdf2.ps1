# Applies the HashCrypt PBKDF2 fix to one OFBiz instance and rebuilds its jar.
#
# Why: in OFBiz 24.09.07 framework/base HashCrypt.doComparePbkdf2 converts the stored salt with
# Arrays.toString(...) before hashing, so the salt no longer matches the one used when the password
# was written - every PBKDF2 password (SHA1/SHA256/SHA384/SHA512) fails to verify. Upstream trunk
# reads the salt back as raw bytes; this script backports exactly that.
#
# Usage:
#   powershell -NoProfile -ExecutionPolicy Bypass -File apply-hashcrypt-pbkdf2.ps1 -Instance F:\build-ofbiz\ofbiz-prod
#   powershell -NoProfile -ExecutionPolicy Bypass -File apply-hashcrypt-pbkdf2.ps1 -Instance F:\build-ofbiz\ofbiz-test -SkipBuild
#
# Idempotent: running it again reports "already patched". Restart the instance afterwards.

param(
    [Parameter(Mandatory = $true)][string]$Instance,
    [string]$Gradle,
    [switch]$SkipBuild
)

$ErrorActionPreference = 'Stop'

$relative = 'framework\base\src\main\java\org\apache\ofbiz\base\crypto\HashCrypt.java'
$file = Join-Path $Instance $relative
if (-not (Test-Path -LiteralPath $file)) {
    throw "HashCrypt.java not found: $file"
}

$badPattern = 'byte\[\] salt = Arrays\.toString\(\s*java\.util\.Base64\.getMimeDecoder\(\)\.decode\(parts\[1\]\.getBytes\(StandardCharsets\.UTF_8\)\)\)\s*\.getBytes\(StandardCharsets\.UTF_8\);'
$goodLine = 'byte[] salt = java.util.Base64.getMimeDecoder().decode(parts[1].getBytes(StandardCharsets.UTF_8));'
$goodPattern = 'byte\[\] salt = java\.util\.Base64\.getMimeDecoder\(\)\.decode\(parts\[1\]\.getBytes\(StandardCharsets\.UTF_8\)\);'

$text = [System.IO.File]::ReadAllText($file)
if ($text -match $badPattern) {
    # keep the original line ending / indentation of the file
    $replacement = $goodLine
    $text = [regex]::Replace($text, $badPattern, $replacement, 1)
    [System.IO.File]::WriteAllText($file, $text)
    Write-Host "patched: $file"
} elseif ($text -match $goodPattern) {
    Write-Host "already patched: $file"
} else {
    Write-Host "unexpected source in $file - not touching it, look at hashcrypt-pbkdf2.patch by hand"
    exit 2
}

if ($SkipBuild) {
    Write-Host "skipped the build (-SkipBuild); run gradlew jar yourself before restarting"
    exit 0
}

# The instances here were built with gradle 8.6 (the wrapper jar is not always present), and
# OFBiz 24.09 does not build with gradle 9, so only 8.x distributions are considered.
$gradlePath = $Gradle
if (-not $gradlePath) {
    $wrapperJar = Join-Path $Instance 'gradle\wrapper\gradle-wrapper.jar'
    if ((Test-Path -LiteralPath (Join-Path $Instance 'gradlew.bat')) -and (Test-Path -LiteralPath $wrapperJar)) {
        $gradlePath = Join-Path $Instance 'gradlew.bat'
    } else {
        $found = Get-ChildItem -Path (Join-Path $env:USERPROFILE '.gradle\wrapper\dists') -Recurse -Filter 'gradle.bat' -ErrorAction SilentlyContinue |
            Where-Object { $_.FullName -like '*\bin\gradle.bat' -and $_.FullName -like '*gradle-8.*' } |
            Sort-Object FullName -Descending |
            Select-Object -First 1
        if ($found) {
            $gradlePath = $found.FullName
        } elseif (Get-Command gradle -ErrorAction SilentlyContinue) {
            $gradlePath = 'gradle'
        }
    }
}
if (-not $gradlePath) {
    throw "no gradle found (no usable wrapper in $Instance, no gradle 8.x under %USERPROFILE%\.gradle\wrapper\dists); pass -Gradle <path>"
}

Write-Host "building with $gradlePath (this takes about a minute)"
Push-Location $Instance
try {
    & $gradlePath --no-daemon --console=plain jar
    $code = $LASTEXITCODE
} finally {
    Pop-Location
}
if ($code -ne 0) {
    throw "gradle jar failed with exit code $code"
}

Write-Host "built. restart the instance (start-prod.bat / stop-prod.bat, or deploy-mfg.bat for the dev instance)"
