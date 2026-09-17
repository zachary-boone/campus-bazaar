@echo off
REM =====================================================
REM  Nginx Stop (Port 8080)
REM  Path: E:\project\nginx-1.18.0\nginx-1.18.0
REM =====================================================
title Nginx Stop

netstat -ano | findstr ":8080" | findstr "LISTENING" >nul 2>&1
if %ERRORLEVEL% NEQ 0 goto NOT_RUNNING

echo.
echo [STOPPING] Nginx (graceful) ...

cd /d "E:\project\nginx-1.18.0\nginx-1.18.0"
nginx.exe -s quit 2>nul

echo [WAIT] polling port 8080 (max 8s) ...
set /a RETRY=0
:N_WAIT
netstat -ano | findstr ":8080" | findstr "LISTENING" >nul 2>&1
if %ERRORLEVEL% NEQ 0 goto STOPPED
set /a RETRY+=1
if %RETRY% GEQ 8 goto FORCE_KILL
ping -n 2 127.0.0.1 >nul
goto N_WAIT

:FORCE_KILL
echo [CLEANUP] Worker still alive, force killing ...
taskkill /F /IM nginx.exe >nul 2>&1
ping -n 3 127.0.0.1 >nul

:STOPPED
netstat -ano | findstr ":8080" | findstr "LISTENING" >nul 2>&1
if %ERRORLEVEL% EQU 0 goto STILL_ALIVE

echo [OK] Nginx stopped
echo.
pause
exit /b 0

:NOT_RUNNING
echo.
echo [INFO] Nginx is not running (port 8080 not listening)
echo.
pause
exit /b 0

:STILL_ALIVE
echo.
echo [WARN] Nginx may still be running, please check manually
echo.
pause
exit /b 1
