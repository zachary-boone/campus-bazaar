@echo off
REM =====================================================
REM  RabbitMQ Start (AMQP 5672 / Management 15672)
REM  Path: E:\tools\rabbitmq_server-3.13.7
REM  Erlang: E:\tools\erl-26.2.5
REM  NOTE: needs about 30s to start. Web UI: http://127.0.0.1:15672 (guest/guest)
REM =====================================================
title RabbitMQ Start

if not exist "E:\tools\erl-26.2.5\bin\erl.exe" goto NO_ERLANG
if not exist "E:\tools\rabbitmq_server-3.13.7\sbin\rabbitmq-server.bat" goto NO_RABBIT

netstat -ano | findstr ":5672" | findstr "LISTENING" >nul 2>&1
if %ERRORLEVEL% EQU 0 goto ALREADY

REM env vars must be set in the SAME cmd process that calls rabbitmq-server.bat
set ERLANG_HOME=E:\tools\erl-26.2.5
set RABBITMQ_BASE=E:\tools\rabbitmq_server-3.13.7\data
set RABBITMQ_LOG_BASE=E:\tools\rabbitmq_server-3.13.7\data\log
set RABBITMQ_MNESIA_BASE=E:\tools\rabbitmq_server-3.13.7\data\mnesia
set RABBITMQ_PID_FILE=E:\tools\rabbitmq_server-3.13.7\data\rabbitmq.pid
set PATH=E:\tools\erl-26.2.5\bin;%PATH%

echo.
echo [STARTING] RabbitMQ (detached mode) ...

cd /d "E:\tools\rabbitmq_server-3.13.7\sbin"
call rabbitmq-server.bat -detached

echo [WAIT] polling port 5672 (RabbitMQ boot time ~30s) ...
set /a RETRY=0
:WAIT_LOOP
netstat -ano | findstr ":5672" | findstr "LISTENING" >nul 2>&1
if %ERRORLEVEL% EQU 0 goto STARTED
set /a RETRY+=1
if %RETRY% GEQ 90 goto FAILED
ping -n 2 127.0.0.1 >nul
goto WAIT_LOOP

:STARTED
echo [OK] RabbitMQ is running
echo.
echo   AMQP port      : 5672
echo   Management UI  : http://127.0.0.1:15672  (guest / guest)
echo.
echo To enable the RabbitMQ listener in Spring Boot, set
echo   set SPRING_RABBIT_LISTENER_AUTOSTARTUP=true
echo before starting the backend.
echo.
pause
exit /b 0

:ALREADY
echo.
echo [INFO] RabbitMQ is already running on port 5672
echo.
pause
exit /b 0

:NO_ERLANG
echo.
echo [FAIL] Erlang not found at E:\tools\erl-26.2.5\bin\erl.exe
echo.
pause
exit /b 1

:NO_RABBIT
echo.
echo [FAIL] RabbitMQ not found at E:\tools\rabbitmq_server-3.13.7\sbin\
echo.
pause
exit /b 1

:FAILED
echo.
echo [FAIL] RabbitMQ failed to start (waited 90s). Check logs:
echo   E:\tools\rabbitmq_server-3.13.7\data\log\
echo.
pause
exit /b 1
