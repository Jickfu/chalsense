@echo off
setlocal
set "CHALSENSE_REPO=%~dp0..\..\.."
cd /d "%CHALSENSE_REPO%"

where node >nul 2>nul
if errorlevel 1 (
  echo [ChalSense] Node.js was not found in PATH. Install Node.js 24 and try again.
  pause
  exit /b 1
)

where npm >nul 2>nul
if errorlevel 1 (
  echo [ChalSense] npm was not found in PATH. Install Node.js 24 with npm and try again.
  pause
  exit /b 1
)

if not exist "node_modules\typescript\bin\tsc" (
  echo [ChalSense] Installing locked development dependencies...
  call npm ci
  if errorlevel 1 goto :failed
)

echo [ChalSense] Building and starting the Widget demo...
call npm run demo:widget
if errorlevel 1 goto :failed
exit /b 0

:failed
echo.
echo [ChalSense] Demo startup failed. See the message above.
pause
exit /b 1
