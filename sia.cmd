@echo off
rem Cliente do SIA e atalhos de ciclo de vida.
rem   sia.cmd start     -> sobe o daemon em segundo plano
rem   sia.cmd stop      -> para o daemon
rem   sia.cmd status    -> doctor
rem   sia.cmd <args>    -> repassa para o CLI (doctor, plugins, run, runs, logs, cancel)
setlocal EnableExtensions
set "ROOT=%~dp0"

if /I "%~1"=="start" (
  call "%ROOT%bin\iniciar-sia.cmd"
  exit /b 0
)
if /I "%~1"=="stop" (
  call "%ROOT%bin\parar-sia.cmd"
  exit /b 0
)
if /I "%~1"=="status" (
  call "%ROOT%sia.cmd" --token dev-token doctor
  exit /b 0
)

set "JAVA_EXE=java"
if defined JAVA_HOME if exist "%JAVA_HOME%\bin\java.exe" set "JAVA_EXE=%JAVA_HOME%\bin\java.exe"
"%JAVA_EXE%" -jar "%ROOT%sia-cli\target\sia-cli-1.0.0-SNAPSHOT-all.jar" %*
endlocal
