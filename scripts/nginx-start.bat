@echo off
REM =====================================================
REM  Nginx Start (Port 8080)
REM  Path: E:\project\nginx-1.18.0\nginx-1.18.0
REM  NOTE: cd into install dir, nginx reads ./conf/nginx.conf by default
REM =====================================================
title Nginx Start

netstat -ano | findstr ":8080" | findstr "LISTENING" >nul 2>&1
if %ERRORLEVEL% EQU 0 goto ALREADY

if not exist "E:\project\nginx-1.18.0\nginx-1.18.0\nginx.exe" goto NO_INSTALL

echo.
echo [STARTING] Nginx ...

cd /d "E:\project\nginx-1.18.0\nginx-1.18.0"
start "Nginx" /min nginx.exe

echo [WAIT] polling port 8080 ...
set /a RETRY=0
:WAIT_LOOP
netstat -ano | findstr ":8080" | findstr "LISTENING" >nul 2>&1
if %ERRORLEVEL% EQU 0 goto STARTED
set /a RETRY+=1
if %RETRY% GEQ 10 goto FAILED
ping -n 2 127.0.0.1 >nul
goto WAIT_LOOP

:STARTED
echo [OK] Nginx is running on port 8080
echo.
echo Test:
curl -s -o nul -w "  http://127.0.0.1:8080/ : %%{http_code}\n" http://127.0.0.1:8080/
echo.
pause
exit /b 0

:ALREADY
echo.
echo [INFO] Nginx is already running on port 8080
echo.
pause
exit /b 0

:NO_INSTALL
echo.
echo [FAIL] nginx.exe not found at:
echo   E:\project\nginx-1.18.0\nginx-1.18.0\
echo.
pause
exit /b 1

:FAILED
echo.
echo [FAIL] Nginx failed to start (waited 10s). Check log:
echo   E:\project\nginx-1.18.0\nginx-1.18.0\logs\error.log
echo.
pause
exit /b 1
