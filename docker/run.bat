@echo off
setlocal
echo ==> Starting JTrac NG container on http://localhost:8888
docker run -d -p 8888:8080 -v jtrac_data:/jtrac-data --name jtrac-ng jtrac-ng:latest
if %ERRORLEVEL% equ 0 (
    echo ==> JTrac NG is running! Access it at: http://localhost:8888
) else (
    echo ==> Failed to start JTrac container.
    exit /b %ERRORLEVEL%
)
