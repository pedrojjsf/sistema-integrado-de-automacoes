@echo off
rem Para o daemon do SIA usando o pid gravado em data\sia.db.lock
setlocal EnableExtensions
set "ROOT=%~dp0..\"
set "LOCK=%ROOT%data\sia.db.lock"

if not exist "%LOCK%" (
  echo SIA nao esta rodando ^(sem lock em %LOCK%^)
  goto :fim
)

set "PID="
for /f "usebackq delims=" %%p in ("%LOCK%") do set "PID=%%p"
if not defined PID (
  echo lock vazio; nada a parar
  del "%LOCK%" >nul 2>&1
  goto :fim
)

taskkill /PID %PID% /F >nul 2>&1
if errorlevel 1 (
  echo [aviso] nao foi possivel encerrar o pid %PID% ^(ja saiu?^)
) else (
  echo SIA parado ^(pid %PID%^)
)
del "%LOCK%" >nul 2>&1
:fim
endlocal
exit /b 0
