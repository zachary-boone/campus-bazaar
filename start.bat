@echo off
chcp 65001 >nul
echo ============================================
echo      校园小黑市交易平台 - 一键启动
echo ============================================
echo.
echo [1/3] 启动 Redis ...
start "Redis" /min "E:\redis-zb\redis-win-x64\redis-server.exe" "E:\redis-zb\redis-win-x64\redis.windows.conf"
timeout /t 2 /nobreak >nul
echo [2/3] 启动 nginx ...
start "Nginx" /min cmd /c "cd /d E:\project\nginx-1.18.0\nginx-1.18.0 && nginx.exe -p E:\project\nginx-1.18.0\nginx-1.18.0\ -c conf\nginx.conf"
timeout /t 1 /nobreak >nul
echo [3/3] 启动后端服务 ...
start "CampusBazaar" cmd /k "cd /d E:\project\campus-bazaar\backend && java -jar target\campus-bazaar-0.0.1-SNAPSHOT.jar --server.port=8081"
echo.
echo 前端页面: http://127.0.0.1:8080
echo 后端接口: http://127.0.0.1:8081
echo.
echo 提示: 关闭后端窗口(CampusBazaar)即停止服务
pause
