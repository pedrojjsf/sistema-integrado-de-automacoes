@echo off
rem Forma simples de iniciar: abre uma janela com o daemon rodando.
rem Duplo clique ou: sia.cmd start
rem Fechar a janela (ou sia.cmd stop) encerra o SIA.
setlocal EnableExtensions
set "ROOT=%~dp0..\"
set "JAR=%ROOT%sia-server\target\sia-server-1.0.0-SNAPSHOT-all.jar"

if not exist "%JAR%" (
  echo [erro] jar do daemon nao encontrado. Rode: mvnw.cmd -DskipTests package
  pause
  goto :fim
)

call :no_ar
if not errorlevel 1 (
  echo SIA ja esta no ar: http://127.0.0.1:8080
  goto :fim
)

echo abrindo a janela do SIA...
start "SIA - Sistema Integrado de Automacoes" cmd /k ""%ROOT%siad.cmd""

for /l %%i in (1,1,45) do (
  call :no_ar
  if not errorlevel 1 (
    echo SIA no ar: http://127.0.0.1:8080
    echo fechar a janela do SIA encerra o servico
    goto :fim
  )
  ping -n 2 127.0.0.1 >nul
)

echo [aviso] ainda nao respondeu. Veja a janela do SIA ou %ROOT%logs\sia.log
:fim
endlocal
exit /b 0

:no_ar
curl.exe -s -o nul -m 2 "http://127.0.0.1:8080/api/health" >nul 2>&1
exit /b %errorlevel%
