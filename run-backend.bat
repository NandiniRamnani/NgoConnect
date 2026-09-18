@echo off
REM ===========================================================================
REM  NGOConnect - start the Spring Boot backend
REM
REM  Double-click this file, or run it from a terminal:  .\run-backend.bat
REM  It exists so the Maven goal never has to be typed by hand - "spring-boot:run"
REM  is a single token and a stray space in it makes Maven fail before it even
REM  reads the project, with a bare NoSuchElementException and no useful message.
REM ===========================================================================

cd /d "%~dp0"

echo.
echo  Freeing port 8082 if a previous run is still holding it...
for /f "tokens=5" %%P in ('netstat -ano ^| findstr /r /c:"LISTENING" ^| findstr ":8082"') do (
    echo    stopping leftover process %%P
    taskkill /PID %%P /F >nul 2>&1
)

echo.
echo  Starting NGOConnect backend on http://localhost:8082
echo  Press Ctrl+C to stop.
echo.

call mvnw.cmd spring-boot:run

REM Keep the window open if the build failed, so the error stays readable.
if errorlevel 1 (
    echo.
    echo  ==========================================================
    echo   Startup failed. The error is printed above.
    echo  ==========================================================
    pause
)
