param([string]$Folder = '')

if ([string]::IsNullOrEmpty($Folder)) { $Folder = Join-Path $PSScriptRoot 'baseline' }
$files = Get-ChildItem $Folder -Filter 'batch-*.json' -ErrorAction SilentlyContinue | Sort-Object Name
if (@($files).Count -eq 0) {
  Write-Host ('no batch-*.json under ' + $Folder)
  exit 1
}

$rows = @()
foreach ($file in $files) {
  $document = Get-Content $file.FullName -Raw -Encoding UTF8 | ConvertFrom-Json
  $runs = @($document.runs)

  # the page is said to hold 23 orders: what comes back in nearly every run is what it really prints
  $all = @()
  foreach ($run in $runs) { $all += @($run.response.orders | ForEach-Object { $_.orderNo }) }
  $frequency = @{}
  foreach ($number in $all) {
    if (-not $frequency.ContainsKey($number)) { $frequency[$number] = 0 }
    $frequency[$number] = $frequency[$number] + 1
  }
  $truth = @($frequency.GetEnumerator() | Where-Object { $_.Value -ge [math]::Ceiling($runs.Count * 0.9) } | ForEach-Object { $_.Key })

  $right = 0
  $extraRow = 0
  $hint = 0
  $copy = 0
  $twice = 0
  $rowsOff = 0
  $money = 0
  foreach ($run in $runs) {
    $response = $run.response
    $orders = @($response.orders)
    $numbers = @($orders | ForEach-Object { $_.orderNo } | Sort-Object -Unique)
    $missing = @($truth | Where-Object { $numbers -notcontains $_ })
    $extra = @($numbers | Where-Object { $truth -notcontains $_ })
    if ($missing.Count -eq 0 -and $extra.Count -eq 0 -and [int]$response.count -eq $truth.Count) { $right++ }
    if ([int]$response.count -gt $truth.Count) { $extraRow++ }
    if ($response.message.Contains('图上有')) { $hint++ }
    foreach ($order in $orders) {
      if (-not $order.note) { continue }
      if ($order.note.Contains('一模一样') -or $order.note.Contains('完全相同')) { $copy++ }
      if ($order.note.Contains('两遍读数不一致')) { $twice++ }
      if ($order.note.Contains('行明细')) { $rowsOff++ }
      if ($order.note.Contains('实付')) { $money++ }
    }
  }

  $rows += [pscustomobject]@{
    Batch = $document.batch
    Runs = $runs.Count
    Right = $right
    Percent = [math]::Round(100 * $right / $runs.Count)
    Orders = $truth.Count
    ExtraRow = $extraRow
    Hint = $hint
    Copy = $copy
    Twice = $twice
    RowsOff = $rowsOff
    Money = $money
  }
}

$rows | Format-Table -AutoSize | Out-String -Width 260 | Write-Host
Write-Host 'what each batch was:'
foreach ($file in $files) {
  $document = Get-Content $file.FullName -Raw -Encoding UTF8 | ConvertFrom-Json
  Write-Host ('  ' + $document.batch + ': ' + $document.note)
}
Write-Host ''
Write-Host 'Right = runs that read every order of the page and no other; ExtraRow = runs with one order too'
Write-Host 'many (a block read twice with an invented number); Hint = those runs where the page said so;'
Write-Host 'Copy / Twice / RowsOff / Money = how many notes of each kind the runs carried.'
