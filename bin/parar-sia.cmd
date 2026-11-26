@echo off
rem Para o daemon do SIA usando o pid gravado em data\sia.db.lock.pid
setlocal EnableExtensions
set "ROOT=%~dp0..\"
set "PIDFILE=%ROOT%data\sia.db.lock.pid"
set "LOCK=%ROOT%data\sia.db.lock"

if not exist "%PIDFILE%" if not exist "%LOCK%" (
  echo SIA nao esta rodando
  goto :fim
)

set "PID="
if exist "%PIDFILE%" (
  for /f "usebackq delims=" %%p in ("%PIDFILE%") do set "PID=%%p"
)

if not defined PID (
  echo lock sem pid legivel; encerrando apenas os arquivos de lock
  del "%PIDFILE%" >nul 2>&1
  del "%LOCK%" >nul 2>&1
  goto :fim
)

taskkill /PID %PID% /F >nul 2>&1
if errorlevel 1 (
  echo [aviso] nao foi possivel encerrar o pid %PID% ^(ja saiu?^)
) else (
  echo SIA parado ^(pid %PID%^)
)
del "%PIDFILE%" >nul 2>&1
del "%LOCK%" >nul 2>&1
:fim
endlocal
exit /b 0
