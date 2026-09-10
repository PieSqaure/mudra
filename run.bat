@echo off
setlocal
cd /d "%~dp0"

echo ==^> Running :core unit tests (sign classification, temporal decoding, Qwen prompt/JSON contract)
call gradlew.bat :core:test
if errorlevel 1 goto :fail

where adb >nul 2>nul
if errorlevel 1 (
    echo.
    echo adb not found - skipping the app build/install.
    echo Install the Android SDK ^(or open this project in Android Studio^) to build and run :app.
    goto :done
)

adb get-state >nul 2>nul
if errorlevel 1 (
    echo.
    echo No connected device or running emulator detected.
    echo Start an emulator or connect a device, then re-run this script to build and launch the app.
    goto :done
)

echo.
echo ==^> Building and installing the debug app
call gradlew.bat :app:installDebug
if errorlevel 1 goto :fail

echo ==^> Launching Mudra
adb shell am start -n com.pisquarelabs.mudra/.MainActivity
goto :done

:fail
echo.
echo Build failed - see the output above.

:done
echo.
pause
