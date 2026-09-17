@echo off
REM =====================================================
REM  RabbitMQ Stop (Port 5672)
REM  Path: E:\tools\rabbitmq_server-3.13.7
REM =====================================================
title RabbitMQ Stop

netstat -ano | findstr ":5672" | findstr "LISTENING" >nul 2>&1
if %ERRORLEVEL% NEQ 0 goto NOT_RUNNING

set ERLANG_HOME=E:\tools\erl-26.2.5
set RABBITMQ_BASE=E:\tools\rabbitmq_server-3.13.7\data
set RABBITMQ_LOG_BASE=E:\tools\rabbitmq_server-3.13.7\data\log
set RABBITMQ_MNESIA_BASE=E:\tools\rabbitmq_server-3.13.7\data\mnesia
set RABBITMQ_PID_FILE=E:\tools\rabbitmq_server-3.13.7\data\rabbitmq.pid
set PATH=E:\tools\erl-26.2.5\bin;%PATH%

echo.
echo [STOPPING] RabbitMQ (graceful via rabbitmqctl) ...

cd /d "E:\tools\rabbitmq_server-3.13.7\sbin"
call rabbitmqctl.bat stop 2>nul

echo [WAIT] polling port 5672 (max 15s) ...
set /a RETRY=0
:R_WAIT
netstat -ano | findstr ":5672" | findstr "LISTENING" >nul 2>&1
if %ERRORLEVEL% NEQ 0 goto STOPPED
set /a RETRY+=1
if %RETRY% GEQ 15 goto FORCE_KILL
ping -n 2 127.0.0.1 >nul
goto R_WAIT

:FORCE_KILL
echo [CLEANUP] Erlang VM still alive, force killing ...
taskkill /F /IM epmd.exe >nul 2>&1
taskkill /F /IM erl.exe >nul 2>&1
taskkill /F /IM beam.smp.exe >nul 2>&1
ping -n 3 127.0.0.1 >nul

:STOPPED
netstat -ano | findstr ":5672" | findstr "LISTENING" >nul 2>&1
if %ERRORLEVEL% EQU 0 goto STILL_ALIVE

echo [OK] RabbitMQ stopped
echo.
pause
exit /b 0

:NOT_RUNNING
echo.
echo [INFO] RabbitMQ is not running (port 5672 not listening)
echo.
pause
exit /b 0

:STILL_ALIVE
echo.
echo [WARN] RabbitMQ may still be running, please check manually
echo.
pause
exit /b 1
