@echo off
REM =====================================================
REM  Redis Stop (Port 6379)
REM  Path: E:\redis-zb\redis-win-x64
REM =====================================================
title Redis Stop

netstat -ano | findstr ":6379" | findstr "LISTENING" >nul 2>&1
if %ERRORLEVEL% NEQ 0 goto NOT_RUNNING

echo.
echo [STOPPING] Redis (graceful shutdown) ...

REM graceful: save-less shutdown, then exit
"E:\redis-zb\redis-win-x64\redis-cli.exe" -h 127.0.0.1 -p 6379 shutdown nosave 2>nul

echo [WAIT] polling port 6379 (max 8s) ...
set /a RETRY=0
:WAIT_LOOP
netstat -ano | findstr ":6379" | findstr "LISTENING" >nul 2>&1
if %ERRORLEVEL% NEQ 0 goto STOPPED
set /a RETRY+=1
if %RETRY% GEQ 8 goto FORCE_KILL
ping -n 2 127.0.0.1 >nul
goto WAIT_LOOP

:FORCE_KILL
echo [CLEANUP] Still alive, force killing by port PID ...
for /f "tokens=5" %%a in ('netstat -ano ^| findstr ":6379" ^| findstr "LISTENING"') do taskkill /F /PID %%a >nul 2>&1
ping -n 3 127.0.0.1 >nul

:STOPPED
netstat -ano | findstr ":6379" | findstr "LISTENING" >nul 2>&1
if %ERRORLEVEL% EQU 0 goto STILL_ALIVE

echo [OK] Redis stopped
echo.
pause
exit /b 0

:NOT_RUNNING
echo.
echo [INFO] Redis is not running (port 6379 not listening)
echo.
pause
exit /b 0

:STILL_ALIVE
echo.
echo [WARN] Redis may still be running, please check manually
echo.
pause
exit /b 1
