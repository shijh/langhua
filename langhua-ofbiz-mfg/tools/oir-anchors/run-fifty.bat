@echo off
rem Runs one screenshot through the vision scan of the dev instance N times, so a change of the
rem reading can be held against the baseline in this folder.
rem
rem   run-fifty.bat "D:\Downloads\shot.png" 50 F:\build-ofbiz\runs
rem
rem Writes run-1.json .. run-N.json into the folder given as the third argument.
setlocal
if "%~1"=="" (
  echo usage: run-fifty.bat "D:\path\screenshot.png" [count] [output folder]
  exit /b 1
)
set "IMG=%~1"
set "COUNT=%~2"
if "%COUNT%"=="" set "COUNT=50"
set "OUT=%~3"
if "%OUT%"=="" set "OUT=F:\build-ofbiz\runs"
set "CK=%TEMP%\oir-runs-cookie.txt"
if not exist "%OUT%" mkdir "%OUT%"
del /q "%CK%" 2>nul
del /q "%OUT%\run-*.json" 2>nul
curl.exe -k -s -L --compressed -c "%CK%" -o nul -d "USERNAME=admin&PASSWORD=ofbiz&JavaScriptEnabled=Y" https://localhost:8443/mfg/control/login
for /L %%i in (1,1,%COUNT%) do curl.exe -k -s --compressed -b "%CK%" -o "%OUT%\run-%%i.json" -F "image=@\"%IMG%\";type=image/png" https://localhost:8443/mfg/control/orderImage
echo done: %COUNT% runs into %OUT%
endlocal
