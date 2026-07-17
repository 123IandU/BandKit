@echo off
chcp 65001 >nul
setlocal enabledelayedexpansion

:menu
cls
echo ========================================
echo       BandKit Build Script
echo ========================================
echo.
echo   [1] Build Android APK (Debug)
echo   [2] Build Android APK (Release)
echo   [3] Build Desktop EXE
echo   [4] Run Desktop App
echo   [5] Run Web App (WasmJS)
echo   [6] Clean Build Outputs
echo   [0] Exit
echo.
echo ========================================
set /p choice="Please select (0-6): "

if "%choice%"=="1" goto android_debug
if "%choice%"=="2" goto android_release
if "%choice%"=="3" goto desktop_exe
if "%choice%"=="4" goto desktop_run
if "%choice%"=="5" goto web_run
if "%choice%"=="6" goto clean
if "%choice%"=="0" goto exit
echo Invalid option!
timeout /t 2 >nul
goto menu

:android_debug
cls
echo Building Android APK (Debug)...
echo.
call gradlew.bat :androidApp:assembleDebug
if !errorlevel! equ 0 (
    echo.
    echo ========================================
    echo   Build Successful!
    echo   APK: androidApp\build\outputs\apk\debug\
    echo ========================================
) else (
    echo.
    echo ========================================
    echo   Build Failed!
    echo ========================================
)
pause
goto menu

:android_release
cls
echo Building Android APK (Release)...
echo.
call gradlew.bat :androidApp:assembleRelease
if !errorlevel! equ 0 (
    echo.
    echo ========================================
    echo   Build Successful!
    echo   APK: androidApp\build\outputs\apk\release\
    echo ========================================
) else (
    echo.
    echo ========================================
    echo   Build Failed!
    echo ========================================
)
pause
goto menu

:desktop_exe
cls
echo Building Desktop EXE...
echo.
call gradlew.bat :desktopApp:packageExe
if !errorlevel! equ 0 (
    echo.
    echo ========================================
    echo   Build Successful!
    echo   EXE: desktopApp\build\compose\binaries\main\exe\
    echo ========================================
) else (
    echo.
    echo ========================================
    echo   Build Failed!
    echo ========================================
)
pause
goto menu

:desktop_run
cls
echo Running Desktop App...
echo.
call gradlew.bat :desktopApp:run
pause
goto menu

:web_run
cls
echo Running Web App (WasmJS)...
echo.
call gradlew.bat :webApp:wasmJsBrowserDevelopmentRun
pause
goto menu

:clean
cls
echo Cleaning Build Outputs...
echo.
call gradlew.bat clean
echo.
echo ========================================
echo   Clean Complete!
echo ========================================
pause
goto menu

:exit
exit /b 0
