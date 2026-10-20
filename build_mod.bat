@echo off
rem NOTE: keep this file ASCII-only. cmd.exe parses .bat with the OEM codepage (GBK on zh-CN),
rem so UTF-8 Chinese comments get mis-decoded, swallow line endings and break parsing.
rem
rem Always build in this script's own directory (the project root).
rem The old hard-coded target d:\pcl\.minecraft\versions\...\eternal_heart no longer exists,
rem so cd failed and gradlew silently ran in the caller's working directory.
cd /d "%~dp0"
set "JAVA_HOME=C:\Program Files\Java\jdk-17"
set "PATH=%JAVA_HOME%\bin;%PATH%"
echo JAVA_HOME=%JAVA_HOME%
java -version
echo.
echo Starting Gradle build from: %CD%
rem Daily build: do NOT add clean.
rem clean deletes build\, forcing mappings, full recompile, processResources, reobfJar,
rem gametest compile and the artifact self-check to rerun on every build.
rem That is why one build used to take over a minute.
rem Need a full rebuild? Run this manually instead: gradlew.bat clean build
call gradlew.bat build
if %ERRORLEVEL% NEQ 0 (
    echo BUILD FAILED
    exit /b 1
)
echo BUILD SUCCESS
echo Artifact: %CD%\build\libs
