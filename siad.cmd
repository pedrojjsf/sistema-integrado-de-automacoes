@echo off
rem Sobe o daemon do SIA. Uso: siad.cmd [config]
setlocal
set "JAVA_EXE=java"
if defined JAVA_HOME if exist "%JAVA_HOME%\bin\java.exe" set "JAVA_EXE=%JAVA_HOME%\bin\java.exe"
set "CONFIG=%~1"
if "%CONFIG%"=="" set "CONFIG=%~dp0config\local.yml"
"%JAVA_EXE%" -jar "%~dp0sia-server\target\sia-server-1.0.0-SNAPSHOT-all.jar" "%CONFIG%"
endlocal
