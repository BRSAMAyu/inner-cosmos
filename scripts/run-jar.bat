@echo off
chcp 65001 >nul
cd /d "%~dp0"
echo ============================================
echo   Inner Cosmos - starting...
echo   Default: H2 file DB + Mock LLM (no API key)
echo   Open: http://localhost:8080/pages/index.html
echo   Accounts: admin/admin123  demo/demo123
echo ============================================
java -jar inner-cosmos-0.1.0.jar
pause
