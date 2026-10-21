# Generates the website icons (tab icon + navigation logo) from one square PNG.
#
#   powershell -ExecutionPolicy Bypass -File tools\make-icons.ps1 -Source F:\build-ofbiz\langhua-logo.png
#
# The white backdrop of the source is turned transparent first, because the pages use a dark
# theme. Output goes to webapp/mfg/images and, when the sibling component is next to this one,
# to webapp/learningcurve/images as well.
param(
    [Parameter(Mandatory = $true)][string]$Source,
    [string]$MfgDir,
    [string]$LcDir
)

Add-Type -AssemblyName System.Drawing

if (-not $MfgDir) { $MfgDir = Join-Path $PSScriptRoot '..\webapp\mfg\images' }
if (-not $LcDir) { $LcDir = Join-Path $PSScriptRoot '..\..\langhua-ofbiz-learningcurve\webapp\learningcurve\images' }

if (-not (Test-Path $Source)) { throw "source not found: $Source" }

$img = [System.Drawing.Image]::FromFile((Resolve-Path $Source))
$w = $img.Width
$h = $img.Height
$flat = New-Object System.Drawing.Bitmap $w, $h, ([System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
$g = [System.Drawing.Graphics]::FromImage($flat)
$g.DrawImage($img, 0, 0, $w, $h)
$g.Dispose()
$img.Dispose()

# white (and near white) becomes transparent; the ramp keeps the anti-aliased edges smooth
$minX = $w; $minY = $h; $maxX = -1; $maxY = -1
for ($y = 0; $y -lt $h; $y++) {
    for ($x = 0; $x -lt $w; $x++) {
        $p = $flat.GetPixel($x, $y)
        $m = [Math]::Min($p.R, [Math]::Min($p.G, $p.B))
        if ($m -ge 250) { $a = 0 }
        elseif ($m -le 205) { $a = 255 }
        else { $a = [int](255 * (250 - $m) / 45) }
        if ($a -ne $p.A) { $flat.SetPixel($x, $y, [System.Drawing.Color]::FromArgb($a, $p.R, $p.G, $p.B)) }
        if ($a -gt 20) {
            if ($x -lt $minX) { $minX = $x }
            if ($x -gt $maxX) { $maxX = $x }
            if ($y -lt $minY) { $minY = $y }
            if ($y -gt $maxY) { $maxY = $y }
        }
    }
}
$boxW = $maxX - $minX + 1
$boxH = $maxY - $minY + 1
Write-Output ("mark box {0}x{1} at {2},{3}" -f $boxW, $boxH, $minX, $minY)

function Save-Square($source, $boxX, $boxY, $boxW, $boxH, [int]$size, [string]$path) {
    $canvas = New-Object System.Drawing.Bitmap $size, $size, ([System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
    $g = [System.Drawing.Graphics]::FromImage($canvas)
    $g.Clear([System.Drawing.Color]::Transparent)
    $g.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
    $g.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::HighQuality
    $g.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::HighQuality
    $inner = $size * 0.92
    $scale = [Math]::Min($inner / $boxW, $inner / $boxH)
    $dw = $boxW * $scale
    $dh = $boxH * $scale
    $dest = New-Object System.Drawing.RectangleF ([single](($size - $dw) / 2)), ([single](($size - $dh) / 2)), ([single]$dw), ([single]$dh)
    $src = New-Object System.Drawing.RectangleF ([single]$boxX), ([single]$boxY), ([single]$boxW), ([single]$boxH)
    $g.DrawImage($source, $dest, $src, [System.Drawing.GraphicsUnit]::Pixel)
    $g.Dispose()
    $canvas.Save($path, [System.Drawing.Imaging.ImageFormat]::Png)
    $canvas.Dispose()
    Write-Output ("  wrote " + $path)
}

foreach ($dir in @($MfgDir, $LcDir)) {
    if (-not (Test-Path $dir)) { New-Item -ItemType Directory -Path $dir -Force | Out-Null }
    Save-Square $flat $minX $minY $boxW $boxH 512 (Join-Path $dir 'logo.png')
    Save-Square $flat $minX $minY $boxW $boxH 180 (Join-Path $dir 'logo-180.png')
    Save-Square $flat $minX $minY $boxW $boxH 64  (Join-Path $dir 'logo-64.png')
    Save-Square $flat $minX $minY $boxW $boxH 32  (Join-Path $dir 'logo-32.png')
}
$flat.Dispose()
