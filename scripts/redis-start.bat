@echo off
REM =====================================================
REM  Redis Start (Port 6379)
REM  Path: E:\redis-zb\redis-win-x64
REM =====================================================
title Redis Start

netstat -ano | findstr ":6379" | findstr "LISTENING" >nul 2>&1
if %ERRORLEVEL% EQU 0 goto ALREADY

echo.
echo [STARTING] Redis ...

cd /d "E:\redis-zb\redis-win-x64"
if not exist "redis-server.exe" goto NO_INSTALL

start "Redis" /min redis-server.exe redis.windows.conf --logfile "redis.log"

echo [WAIT] polling port 6379 ...
set /a RETRY=0
:WAIT_LOOP
netstat -ano | findstr ":6379" | findstr "LISTENING" >nul 2>&1
if %ERRORLEVEL% EQU 0 goto STARTED
set /a RETRY+=1
if %RETRY% GEQ 15 goto FAILED
ping -n 2 127.0.0.1 >nul
goto WAIT_LOOP

:STARTED
echo [OK] Redis is running on port 6379
echo.
echo Test connection:
"E:\redis-zb\redis-win-x64\redis-cli.exe" -h 127.0.0.1 -p 6379 ping
echo.
pause
exit /b 0

:ALREADY
echo.
echo [INFO] Redis is already running on port 6379
echo.
pause
exit /b 0

:NO_INSTALL
echo.
echo [FAIL] redis-server.exe not found in E:\redis-zb\redis-win-x64
echo.
pause
exit /b 1

:FAILED
echo.
echo [FAIL] Redis failed to start (waited 15s). Check log:
echo   E:\redis-zb\redis-win-x64\redis.log
echo.
pause
exit /b 1
