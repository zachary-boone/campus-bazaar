@echo off
REM =====================================================
REM  Campus-Bazaar Backend Start (Port 8081)
REM  Path: E:\project\campus-bazaar\backend
REM  NOTE: Spring Boot needs about 10-20s to start
REM =====================================================
title CampusBazaar Backend Start

netstat -ano | findstr ":8081" | findstr "LISTENING" >nul 2>&1
if %ERRORLEVEL% EQU 0 goto ALREADY

set BACKEND_HOME=E:\project\campus-bazaar\backend
set JAR_FILE=%BACKEND_HOME%\target\campus-bazaar.jar

if not exist "%JAR_FILE%" goto NO_JAR

REM pre-check: Redis MUST be running.
REM Redisson connects during bean init, so the app hard-fails if Redis is down.
netstat -ano | findstr ":6379" | findstr "LISTENING" >nul 2>&1
if %ERRORLEVEL% NEQ 0 goto NO_REDIS

echo.
echo [STARTING] Backend (Spring Boot) ...

cd /d "%BACKEND_HOME%"
REM window title "CampusBazaar" is used by backend-stop.bat for precise kill
start "CampusBazaar" /min cmd /c "java -jar target\campus-bazaar.jar --server.port=8081 > run.log 2>&1"

echo [WAIT] polling port 8081 (Spring Boot boot time ~15s) ...
set /a RETRY=0
:WAIT_LOOP
netstat -ano | findstr ":8081" | findstr "LISTENING" >nul 2>&1
if %ERRORLEVEL% EQU 0 goto STARTED
set /a RETRY+=1
if %RETRY% GEQ 40 goto FAILED
ping -n 2 127.0.0.1 >nul
goto WAIT_LOOP

:STARTED
echo [OK] Backend is running on port 8081
echo.
echo Test:
curl -s -m 5 -o nul -w "  /goods/category/list : %%{http_code}\n" http://127.0.0.1:8081/goods/category/list
echo.
pause
exit /b 0

:ALREADY
echo.
echo [INFO] Backend is already running on port 8081
echo.
pause
exit /b 0

:NO_JAR
echo.
echo [FAIL] JAR not found: %JAR_FILE%
echo Build it first:
echo   cd /d "%BACKEND_HOME%"
echo   E:\tools\apache-maven-3.9.16\bin\mvn.cmd package -DskipTests
echo.
pause
exit /b 1

:NO_REDIS
echo.
echo [FAIL] Redis is NOT running on port 6379.
echo Redisson connects to Redis during startup, so the backend cannot boot
echo without it.
echo.
echo Please run redis-start.bat first, then retry this script.
echo.
pause
exit /b 1

:FAILED
echo.
echo [FAIL] Backend failed to start (waited 40s). Check log:
echo   %BACKEND_HOME%\run.log
echo.
pause
exit /b 1
