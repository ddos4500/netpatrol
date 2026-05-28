@echo off
setlocal
if not exist target\netpatrol-1.2.0.jar (
  call build.bat
)
java -jar target\netpatrol-1.2.0.jar
