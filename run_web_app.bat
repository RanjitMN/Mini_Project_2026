@echo off
echo ========================================================
echo  Village Energy Coordinator - Web UI & Database Server
echo ========================================================
echo.
echo Make sure MySQL is started in XAMPP Control Panel.
echo Compiling and starting server on http://localhost:8080 ...
echo.
javac -cp ".;lib/*" *.java
if %ERRORLEVEL% NEQ 0 (
    echo Compilation failed! Please check your Java installation.
    pause
    exit /b %ERRORLEVEL%
)
start http://localhost:8080
java -cp ".;lib/*" WebServer
pause
