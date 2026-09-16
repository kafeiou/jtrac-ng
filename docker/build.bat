@echo off
setlocal
cd /d "%~dp0"
echo ======================================================================
echo [Tip] If 'git pull' fails before build (e.g. tag clobber or local diff):
echo       * Quick reset (Recommended): git fetch --tags -f ^&^& git reset --hard origin/master
echo       * Force pull tags:           git pull --tags -f
echo       * One-click alias:           git config --global alias.sync "!git fetch --tags -f ^&^& git reset --hard origin/master"
echo ======================================================================
echo.
echo ==> Building JTrac NG Docker image from context: ..
docker build -f Dockerfile -t jtrac-ng:latest -t jtrac-ng:3.0.0-beta ..
if %ERRORLEVEL% equ 0 (
    echo ==> Build complete! Images: jtrac-ng:latest, jtrac-ng:3.0.0-beta
) else (
    echo ==> Build failed with error code %ERRORLEVEL%
    exit /b %ERRORLEVEL%
)
