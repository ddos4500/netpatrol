@echo off
setlocal
set "MAVEN_CMD=mvn"
if defined MAVEN_HOME if exist "%MAVEN_HOME%\bin\mvn.cmd" set "MAVEN_CMD=%MAVEN_HOME%\bin\mvn.cmd"
if exist "C:\Tools\apache-maven-3.9.11\bin\mvn.cmd" set "MAVEN_CMD=C:\Tools\apache-maven-3.9.11\bin\mvn.cmd"
call "%MAVEN_CMD%" -DskipTests clean package
if errorlevel 1 (
  echo Build failed.
  exit /b 1
)
echo Build complete. Run with run.bat
