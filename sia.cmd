@echo off
rem Cliente do SIA. Uso: sia.cmd --token dev-token doctor
setlocal
set "JAVA_EXE=java"
if defined JAVA_HOME if exist "%JAVA_HOME%\bin\java.exe" set "JAVA_EXE=%JAVA_HOME%\bin\java.exe"
"%JAVA_EXE%" -jar "%~dp0sia-cli\target\sia-cli-1.0.0-SNAPSHOT-all.jar" %*
endlocal
