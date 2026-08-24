@echo off
chcp 65001 >nul
echo ============================================
echo      校园小黑市交易平台 - 一键停止
echo ============================================
echo.

echo [1/4] 停止后端 (端口 8081) ...
for /f "tokens=5" %%a in ('netstat -ano ^| findstr ":8081" ^| findstr "LISTENING"') do taskkill /F /PID %%a >nul 2>&1

echo [2/4] 停止 Nginx ...
taskkill /F /IM nginx.exe >nul 2>&1

echo [3/4] 停止 Redis ...
taskkill /F /IM redis-server.exe >nul 2>&1

echo [4/4] 停止 RabbitMQ ...
set "ERLANG_HOME=E:\tools\erl-26.2.5"
set "RABBITMQ_BASE=E:\tools\rabbitmq_server-3.13.7\data"
set "PATH=E:\tools\erl-26.2.5\bin;%PATH%"
cd /d E:\tools\rabbitmq_server-3.13.7\sbin
call rabbitmqctl.bat stop >nul 2>&1
if errorlevel 1 (
    echo      rabbitmqctl 优雅停止失败，强制结束 Erlang 进程
    taskkill /F /IM erl.exe >nul 2>&1
    taskkill /F /IM beam.smp.exe >nul 2>&1
)
timeout /t 2 /nobreak >nul

echo.
echo 所有服务已停止，可以安全关机。
pause
