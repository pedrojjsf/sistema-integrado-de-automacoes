@echo off
rem Sobe o daemon do SIA em primeiro plano. Uso: siad.cmd [config.yml]
rem Para subir em segundo plano com janela propria: sia.cmd start
setlocal EnableExtensions
set "ROOT=%~dp0"
set "JAR=%ROOT%sia-server\target\sia-server-1.0.0-SNAPSHOT-all.jar"

set "JAVA_EXE=java"
if defined JAVA_HOME if exist "%JAVA_HOME%\bin\java.exe" set "JAVA_EXE=%JAVA_HOME%\bin\java.exe"

if not exist "%JAR%" (
  echo [erro] jar do daemon nao encontrado:
  echo        %JAR%
  echo        rode: mvnw.cmd -DskipTests package
  goto :erro
)

set "CONFIG=%~1"
if "%CONFIG%"=="" set "CONFIG=%ROOT%config\local.yml"
if not exist "%CONFIG%" (
  echo [erro] configuracao nao encontrada: %CONFIG%
  goto :erro
)

"%JAVA_EXE%" -jar "%JAR%" "%CONFIG%"
if errorlevel 1 goto :erro
endlocal
exit /b 0

:erro
echo.
echo [erro] o SIA nao subiu. A mensagem acima explica o motivo.
echo        lock travado? confira data\sia.db.lock e se existe processo java rodando.
echo.
pause
exit /b 1
