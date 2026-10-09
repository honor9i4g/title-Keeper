@echo off
set "GRADLE_USER_HOME=%~dp0..\work\gradle-user-home"
"C:\gradle-9.7.1\bin\gradle.bat" build --no-daemon --console=plain
if errorlevel 1 pause
