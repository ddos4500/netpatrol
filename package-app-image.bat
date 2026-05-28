@echo off
setlocal
set "APP_NAME=NetPatrol"
set "MAIN_CLASS=com.netpatrol.app.NetPatrolApp"
set "DIST_DIR=dist"
set "JAR_OUT_DIR=%DIST_DIR%\jar"
set "IMAGE_OUT_DIR=%DIST_DIR%\app-image"
set "TOOL_JPACKAGE=jpackage"
set "MAVEN_CMD=mvn"

if defined JAVA_HOME if exist "%JAVA_HOME%\bin\jpackage.exe" set "TOOL_JPACKAGE=%JAVA_HOME%\bin\jpackage.exe"
if exist "C:\Program Files\Eclipse Adoptium\jdk-21.0.11.10-hotspot\bin\jpackage.exe" set "TOOL_JPACKAGE=C:\Program Files\Eclipse Adoptium\jdk-21.0.11.10-hotspot\bin\jpackage.exe"
if defined MAVEN_HOME if exist "%MAVEN_HOME%\bin\mvn.cmd" set "MAVEN_CMD=%MAVEN_HOME%\bin\mvn.cmd"
if exist "C:\Tools\apache-maven-3.9.11\bin\mvn.cmd" set "MAVEN_CMD=C:\Tools\apache-maven-3.9.11\bin\mvn.cmd"

if not exist "%TOOL_JPACKAGE%" (
  where jpackage >nul 2>nul
  if errorlevel 1 (
    echo jpackage was not found. Install a full JDK 17 or JDK 21 and set JAVA_HOME.
    exit /b 1
  )
)

if exist "%DIST_DIR%" rmdir /s /q "%DIST_DIR%"
mkdir "%JAR_OUT_DIR%"

call "%MAVEN_CMD%" -DskipTests clean package
if errorlevel 1 exit /b 1

copy /y target\netpatrol-1.2.0.jar "%JAR_OUT_DIR%\netpatrol.jar" >nul

"%TOOL_JPACKAGE%" ^
  --type app-image ^
  --name "%APP_NAME%" ^
  --input "%JAR_OUT_DIR%" ^
  --main-jar netpatrol.jar ^
  --main-class %MAIN_CLASS% ^
  --dest "%IMAGE_OUT_DIR%" ^
  --app-version 1.2.0 ^
  --vendor "NetPatrol"

if errorlevel 1 exit /b 1

echo Package complete:
echo %CD%\%IMAGE_OUT_DIR%\%APP_NAME%\%APP_NAME%.exe
