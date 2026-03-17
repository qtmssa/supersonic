@echo off
setlocal
cd /d "%~dp0" || exit /b 1
node .\scripts\build-fe-prod.cjs
exit /b %errorlevel%
