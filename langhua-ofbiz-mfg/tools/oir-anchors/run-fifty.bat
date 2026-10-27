@echo off
rem Runs one screenshot through the vision scan of the dev instance N times and, when a batch id is
rem given, stores the answers as tools\oir-anchors\baseline\batch-<id>.json in the shape the baseline
rem analysis reads.
rem
rem   run-fifty.bat "D:\Downloads\shot.png" 50 F:\build-ofbiz\runs t12 "Step 158 build"
rem   run-fifty.bat "D:\Downloads\shot.png"              (only runs: 50 times into F:\build-ofbiz\runs)
rem
rem Check first that the jar of the instance is not older than the sources: a batch read with an old
rem jar is worthless.
setlocal
if "%~1"=="" (
  echo usage: run-fifty.bat "D:\path\screenshot.png" [count] [output folder] [batch id] [note]
  exit /b 1
)
set "IMG=%~1"
set "COUNT=%~2"
if "%COUNT%"=="" set "COUNT=50"
set "OUT=%~3"
if "%OUT%"=="" set "OUT=F:\build-ofbiz\runs"
set "ID=%~4"
set "NOTE=%~5"
set "CK=%TEMP%\oir-runs-cookie.txt"
if not exist "%OUT%" mkdir "%OUT%"
del /q "%CK%" 2>nul
del /q "%OUT%\run-*.json" 2>nul
curl.exe -k -s -L --compressed -c "%CK%" -o nul -d "USERNAME=admin&PASSWORD=ofbiz&JavaScriptEnabled=Y" https://localhost:8443/mfg/control/login
for /L %%i in (1,1,%COUNT%) do curl.exe -k -s --compressed -b "%CK%" -o "%OUT%\run-%%i.json" -F "image=@\"%IMG%\";type=image/png" https://localhost:8443/mfg/control/orderImage
echo done: %COUNT% runs into %OUT%
if "%ID%"=="" exit /b 0
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0make-baseline.ps1" -From "%OUT%" -Id "%ID%" -Note "%NOTE%" -Screenshot "%~nx1"
endlocal
