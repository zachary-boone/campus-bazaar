@echo off
REM =====================================================
REM  Campus-Bazaar Backend Stop (Port 8081)
REM  Path: E:\project\campus-bazaar\backend
REM =====================================================
title CampusBazaar Backend Stop

netstat -ano | findstr ":8081" | findstr "LISTENING" >nul 2>&1
if %ERRORLEVEL% NEQ 0 goto NOT_RUNNING

echo.
echo [STOPPING] Backend ...

REM kill by window title first (avoids killing IDEA's java process)
taskkill /F /FI "WINDOWTITLE eq CampusBazaar*" >nul 2>&1

echo [WAIT] polling port 8081 (max 8s) ...
set /a RETRY=0
:B_WAIT
netstat -ano | findstr ":8081" | findstr "LISTENING" >nul 2>&1
if %ERRORLEVEL% NEQ 0 goto STOPPED
set /a RETRY+=1
if %RETRY% GEQ 8 goto FORCE_KILL
ping -n 2 127.0.0.1 >nul
goto B_WAIT

:FORCE_KILL
echo [CLEANUP] Still alive, killing by port PID ...
for /f "tokens=5" %%a in ('netstat -ano ^| findstr ":8081" ^| findstr "LISTENING"') do taskkill /F /PID %%a >nul 2>&1
ping -n 3 127.0.0.1 >nul

:STOPPED
netstat -ano | findstr ":8081" | findstr "LISTENING" >nul 2>&1
if %ERRORLEVEL% EQU 0 goto STILL_ALIVE

echo [OK] Backend stopped
echo.
pause
exit /b 0

:NOT_RUNNING
echo.
echo [INFO] Backend is not running (port 8081 not listening)
echo.
pause
exit /b 0

:STILL_ALIVE
echo.
echo [WARN] Backend may still be running, please check manually
echo.
pause
exit /b 1
