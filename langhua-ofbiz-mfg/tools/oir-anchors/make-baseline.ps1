param(
  [string]$From = '',
  [string]$Id = '',
  [string]$Note = '',
  [string]$Screenshot = '',
  [string]$Out = ''
)

if ([string]::IsNullOrEmpty($From) -or [string]::IsNullOrEmpty($Id)) {
  Write-Host 'usage: make-baseline.ps1 -From <folder with run-N.json> -Id <batch id> [-Note "which build"] [-Screenshot "what it shows"] [-Out <folder>]'
  exit 1
}
if ([string]::IsNullOrEmpty($Out)) { $Out = Join-Path $PSScriptRoot 'baseline' }
New-Item -ItemType Directory -Force -Path $Out | Out-Null

$files = Get-ChildItem $From -Filter 'run-*.json' -ErrorAction SilentlyContinue |
  Sort-Object { [int]([regex]::Replace($_.BaseName, '[^0-9]', '0')) }
if (@($files).Count -eq 0) {
  Write-Host ('no run-*.json under ' + $From)
  exit 1
}

$runs = @()
foreach ($file in $files) {
  $index = [int]([regex]::Replace($file.BaseName, '[^0-9]', '0'))
  $text = [System.IO.File]::ReadAllText($file.FullName, [Text.Encoding]::UTF8)
  $runs += [pscustomobject]@{ run = $index; response = ($text | ConvertFrom-Json) }
}

$document = [pscustomobject]@{
  batch = $Id
  note = $Note
  when = (Get-Date -Format 'yyyy-MM-dd HH:mm')
  screenshot = $Screenshot
  command = 'run-fifty.bat: log in, then N x POST /mfg/control/orderImage with the same picture'
  runs = $runs
}

$target = Join-Path $Out ('batch-' + $Id + '.json')
[System.IO.File]::WriteAllText($target, ($document | ConvertTo-Json -Depth 8 -Compress),
  (New-Object Text.UTF8Encoding($false)))
Write-Host ($target + '  ' + $runs.Count + ' runs, ' + [math]::Round((Get-Item $target).Length / 1024) + ' KB')
Write-Host 'now: powershell -File analyze-baseline.ps1'
