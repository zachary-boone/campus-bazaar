@echo off
REM =====================================================
REM  Frontend Deploy (one-way sync)
REM
REM  SRC : E:\project\campus-bazaar\frontend
REM        ^-- the source you edit (in git)
REM  DST : E:\project\nginx-1.18.0\nginx-1.18.0\html\campus-bazaar
REM        ^-- what nginx actually serves on port 8080
REM
REM  WHY THIS SCRIPT EXISTS:
REM    nginx.conf declares  root html/campus-bazaar  relative to the nginx
REM    install dir. So nginx serves the DEPLOYED copy, NOT campus-bazaar\
REM    frontend. Editing frontend\*.html has no visible effect until the
REM    file is copied over. Symptoms: a new page 404s, or a link still runs
REM    the old handler (e.g. "功能即将上线").
REM
REM  Run this after every frontend edit, then hard-refresh (Ctrl+F5).
REM  Extra files already present in DST are left untouched (no /MIR).
REM =====================================================
title Frontend Deploy

set "SRC=E:\project\campus-bazaar\frontend"
set "DST=E:\project\nginx-1.18.0\nginx-1.18.0\html\campus-bazaar"

if not exist "%SRC%\index.html" goto NO_SRC
if not exist "%DST%\index.html" goto NO_DST

echo.
echo [DEPLOY] %SRC%
echo      -^> %DST%
echo.

xcopy "%SRC%\*" "%DST%\" /E /Y /Q /I
if errorlevel 1 goto FAILED

echo.
echo [OK] Frontend deployed. Hard-refresh the browser (Ctrl+F5).
echo.
pause
exit /b 0

:NO_SRC
echo.
echo [FAIL] Source not found: %SRC%
echo.
pause
exit /b 1

:NO_DST
echo.
echo [FAIL] Nginx html dir not found: %DST%
echo        Expected nginx install at:
echo        E:\project\nginx-1.18.0\nginx-1.18.0
echo.
pause
exit /b 1

:FAILED
echo.
echo [FAIL] xcopy returned an error. Check the paths above.
echo.
pause
exit /b 1
