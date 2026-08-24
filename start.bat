@echo off
chcp 65001 >nul
echo ============================================
echo      校园小黑市交易平台 - 一键启动
echo ============================================
echo.

echo [1/4] 启动 RabbitMQ ...
start "RabbitMQ" /min cmd /c "set ERLANG_HOME=E:\tools\erl-26.2.5&& set RABBITMQ_BASE=E:\tools\rabbitmq_server-3.13.7\data&& set PATH=E:\tools\erl-26.2.5\bin;%PATH%&& cd /d E:\tools\rabbitmq_server-3.13.7\sbin&& rabbitmq-server.bat"
echo      等待 5672 端口就绪 ...
set /a mqwait=0
:wait_mq
netstat -an | findstr ":5672" | findstr "LISTENING" >nul
if not errorlevel 1 goto mq_ok
timeout /t 1 /nobreak >nul
set /a mqwait+=1
if %mqwait% lss 30 goto wait_mq
echo      [警告] RabbitMQ 30 秒内未就绪，请检查 E:\tools\rabbitmq_server-3.13.7\rabbit.log
:mq_ok

echo [2/4] 启动 Redis ...
start "Redis" /min "E:\redis-zb\redis-win-x64\redis-server.exe" "E:\redis-zb\redis-win-x64\redis.windows.conf" --stop-writes-on-bgsave-error no
timeout /t 2 /nobreak >nul

echo [3/4] 启动 Nginx (前端页面) ...
start "Nginx" /min cmd /c "cd /d E:\project\nginx-1.18.0\nginx-1.18.0 && nginx.exe -p E:\project\nginx-1.18.0\nginx-1.18.0\ -c conf\nginx.conf"
timeout /t 1 /nobreak >nul

echo [4/4] 启动后端服务 ...
start "CampusBazaar" cmd /k "cd /d E:\project\campus-bazaar\backend && java -jar target\campus-bazaar.jar --server.port=8081"

echo.
echo 前端页面:     http://127.0.0.1:8080
echo 后端接口:     http://127.0.0.1:8081
echo RabbitMQ管理台: http://127.0.0.1:15672  (guest/guest)
echo.
echo 提示: 关闭后端窗口(CampusBazaar)即停止后端; Redis/Nginx/RabbitMQ 请用 stop.bat 停止
pause
