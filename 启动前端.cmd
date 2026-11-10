@echo off
chcp 65001 >nul
setlocal
title Orbit - 前端开发服务
pushd "%~dp0"
if errorlevel 1 goto :failed

where node.exe >nul 2>&1
if errorlevel 1 (
  echo 未找到 Node.js。请安装 Node.js 22.12 或更高版本后重新启动。
  goto :failed
)

if not exist "node_modules\vite\bin\vite.js" (
  where npm.cmd >nul 2>&1
  if errorlevel 1 (
    echo 未找到 npm。请检查 Node.js 是否安装完整。
    goto :failed
  )
  echo 首次运行，正在安装前端依赖，请保持网络连接……
  call npm.cmd ci
  if errorlevel 1 goto :failed
)

if not defined ORBIT_FRONTEND_PORT set "ORBIT_FRONTEND_PORT=5173"
if not defined ORBIT_BACKEND_URL set "ORBIT_BACKEND_URL=http://127.0.0.1:8080"
echo.
echo 正在启动研迹 Orbit 前端……
echo 网页地址：http://127.0.0.1:%ORBIT_FRONTEND_PORT%
echo 后端地址：%ORBIT_BACKEND_URL%
echo 请保持本窗口开启；停止前端请按 Ctrl+C。
echo 后端请在 IDEA 中单独启动 OrbitApplication。
echo.
node "scripts\run-tool.cjs" dev
if errorlevel 1 goto :failed
popd
endlocal
exit /b 0

:failed
echo.
echo 前端未能启动，请查看上方的错误信息。
echo 如果该端口已有 Orbit 前端在运行，直接访问网页即可，无需重复启动。
popd
pause
endlocal
exit /b 1
