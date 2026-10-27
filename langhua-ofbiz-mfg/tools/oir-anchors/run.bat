@echo off
rem Measures the orange button detection on a real screenshot, using the classes of the dev instance.
rem
rem   run.bat "D:\path\to\screenshot.png"
rem
rem Uses %JAVA_HOME% when it is set, otherwise java / javac from the path. The instance only has to
rem be built once (any of the three does); its ofbiz.jar holds the classes of the plugin.
setlocal
set "OFBIZ=F:\build-ofbiz\apache-ofbiz-24.09.07"
if defined JAVA_HOME (
  set "JAVA=%JAVA_HOME%\bin\java.exe"
  set "JAVAC=%JAVA_HOME%\bin\javac.exe"
) else (
  set "JAVA=java"
  set "JAVAC=javac"
)
if not exist "%OFBIZ%\build\libs\ofbiz.jar" (
  echo ofbiz.jar not found under %OFBIZ% - edit OFBIZ in this file
  exit /b 1
)
if "%~1"=="" (
  echo usage: run.bat "D:\path\to\screenshot.png"
  exit /b 1
)
set "CP=%~dp0;%OFBIZ%\build\libs\ofbiz.jar;%OFBIZ%\lib\*"
"%JAVAC%" -nowarn -encoding UTF-8 -cp "%CP%" -d "%~dp0." "%~dp0AnchorAudit.java" "%~dp0LongestOrange.java" || exit /b 1
echo ==== what the shipped code does ====
"%JAVA%" -Dfile.encoding=UTF-8 -cp "%CP%" AnchorAudit "%~1"
echo.
echo ==== every long orange band of the page ====
"%JAVA%" -Dfile.encoding=UTF-8 -cp "%~dp0." LongestOrange "%~1"
endlocal
