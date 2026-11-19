@echo off
rem Verifica os tipos do frontend (app.js) contra types/api.d.ts.
rem Nao faz parte do build Java: rode quando mexer em sia-server/src/main/resources/web.
setlocal EnableExtensions
set "ROOT=%~dp0.."

if not exist "%ROOT%\node_modules\.bin\tsc.cmd" (
  echo [aviso] TypeScript nao instalado. Rode uma vez:
  echo         cd /d "%ROOT%" ^&^& npm install
  echo         ^(ou: npm install -g typescript ^&^& tsc -p "%ROOT%\tsconfig.json"^)
  exit /b 1
)

cd /d "%ROOT%"
call "%ROOT%\node_modules\.bin\tsc.cmd" -p tsconfig.json
if errorlevel 1 (
  echo [erro] tipos inconsistentes com o contrato
  exit /b 1
)
echo tipos ok
endlocal
exit /b 0
