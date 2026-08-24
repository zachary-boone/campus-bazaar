@echo off
echo ============================================
echo      校园小黑市交易平台 - 一键停止
echo ============================================
echo.
echo [1/3] 停止后端 (端口 8081) ...
for /f "tokens=5" %%a in ('netstat -ano ^| findstr ":8081" ^| findstr "LISTENING"') do taskkill /F /PID %%a >nul 2>&1
echo [2/3] 停止 nginx ...
taskkill /F /IM nginx.exe >nul 2>&1
echo [3/3] 停止 Redis ...
taskkill /F /IM redis-server.exe >nul 2>&1
timeout /t 1 /nobreak >nul
echo.
echo 所有服务已停止，可以安全关机。
pause
