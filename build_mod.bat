@echo off
cd /d "d:\pcl\.minecraft\versions\「莱特兰：魂影与恶域」\eternal_heart"
set "JAVA_HOME=C:\Program Files\Java\jdk-17"
set "PATH=%JAVA_HOME%\bin;%PATH%"
echo JAVA_HOME=%JAVA_HOME%
java -version
echo.
echo Starting Gradle build from: %CD%
call gradlew.bat clean build
if %ERRORLEVEL% NEQ 0 (
    echo BUILD FAILED
    exit /b 1
)
echo BUILD SUCCESS
