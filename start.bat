@echo off
setlocal
cd /d "%~dp0"
title KitchenHQ - Local Application

echo.
echo  KITCHENHQ - FRONTEND + JAVA BACKEND
 echo ======================================

where node >nul 2>nul
if errorlevel 1 (
  echo [ERROR] Node.js was not found. Install Node.js 20 or newer from https://nodejs.org/
  pause
  exit /b 1
)
where npm >nul 2>nul
if errorlevel 1 (
  echo [ERROR] npm was not found. Reinstall Node.js with npm included.
  pause
  exit /b 1
)
where mvn >nul 2>nul
if errorlevel 1 (
  echo [ERROR] Apache Maven was not found. Install Maven 3.8+ and add it to PATH.
  pause
  exit /b 1
)
where java >nul 2>nul
if errorlevel 1 (
  echo [ERROR] Java was not found. Install JDK 17 or newer and add it to PATH.
  pause
  exit /b 1
)

for /f "tokens=3" %%v in ('java -version 2^>^&1 ^| findstr /i "version"') do set JAVAVER=%%v
echo [1/4] Installing frontend packages (first run may take a few minutes)...
cd frontend
if not exist node_modules call npm install --no-audit --no-fund
if errorlevel 1 goto fail

echo [2/4] Building the React frontend...
call npm run build
if errorlevel 1 goto fail
cd ..

echo [3/4] Running Java tests and compiling the backend...
call mvn -q test
if errorlevel 1 goto fail

echo [4/4] Starting KitchenHQ...
echo.
echo Open http://localhost:8080 in your browser.
echo Keep this window open while using KitchenHQ. Press Ctrl+C to stop.
echo.
java -cp target/classes com.rasoiroute.api.RasoiServer 8080
goto end

:fail
echo.
echo [ERROR] Setup or build failed. Read the message above, fix the issue, and run start.bat again.
pause
exit /b 1
:end
endlocal
