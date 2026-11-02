# Builds the little square that goes in the middle of a QR code (webapp/mfg/images/logo-qr.png).
#
#   powershell -ExecutionPolicy Bypass -File tools\make-qr-logo.ps1
#
# The QR code is black and white, so the mark is drawn on a white tile (the logo files used by the
# pages have a transparent background, and modules would show through it). OFBiz draws the default
# logo at its native size, so the tile is written at the size the codes need: 48 pixels is 12% of a
# 400 px code and 19% of a 250 px one - under the 7% of area an error correction level L code may lose.
param(
    [string]$Source,
    [string]$Target,
    [int]$Size = 48
)

Add-Type -AssemblyName System.Drawing

if (-not $Source) { $Source = Join-Path $PSScriptRoot '..\webapp\mfg\images\logo.png' }
if (-not $Target) { $Target = Join-Path $PSScriptRoot '..\webapp\mfg\images\logo-qr.png' }
if (-not (Test-Path $Source)) { throw "source not found: $Source" }

$img = [System.Drawing.Image]::FromFile((Resolve-Path $Source))
$canvas = New-Object System.Drawing.Bitmap $Size, $Size, ([System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
$g = [System.Drawing.Graphics]::FromImage($canvas)
$g.Clear([System.Drawing.Color]::White)
$g.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
$g.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::HighQuality
$g.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::HighQuality

# the mark fills most of the tile, with a thin white margin of its own
$inner = $Size * 0.86
$scale = [Math]::Min($inner / $img.Width, $inner / $img.Height)
$dw = $img.Width * $scale
$dh = $img.Height * $scale
$g.DrawImage($img, [single](($Size - $dw) / 2), [single](($Size - $dh) / 2), [single]$dw, [single]$dh)
$g.Dispose()
$img.Dispose()

$canvas.Save($Target, [System.Drawing.Imaging.ImageFormat]::Png)
$canvas.Dispose()
Write-Output ("wrote " + $Target + " (" + $Size + "x" + $Size + ")")
