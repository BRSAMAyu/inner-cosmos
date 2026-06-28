@echo off
chcp 65001 >nul
cd /d "%~dp0"
echo ============================================
echo   Inner Cosmos - starting...
echo   Default: H2 file DB + real GLM glm-4.7 (key built-in, no setup)
echo   Startup takes ~15-25s, then open:
echo   http://localhost:8080/pages/index.html
echo   Accounts: demo/demo123 (recommended)  admin/admin123
echo ============================================
java -jar inner-cosmos-0.1.0.jar
pause
