@echo off

:: Load the configuration file
for /F "delims=" %%a in (_config.conf) do set "%%a"

:: Build the derived paths after loading root from config
set "temp=%root%\temp"
set "src=%root%\src"
set "lib=%root%\lib"
set "bin=%root%\bin"

:: Create temp and bin directories if they don't exist
if not exist "%temp%" mkdir "%temp%"
if not exist "%bin%" mkdir "%bin%"

:: Copy all java files to temp directory
for /r "%src%" %%f in (*.java) do (
    xcopy "%%f" "%temp%"
)

:: Move to temp to compile all java files
cd "%temp%"
javac -d "%bin%" -cp "%lib%\*" *.java

:: Move to bin to create jar
cd "%bin%"
jar -cvf "%jar_name%.jar" .

:: Copy jar to target directory
copy "%jar_name%.jar" "%target_dir%"

:: Clean up
cd "%root%"
rmdir /s /q "%temp%"

echo Compilation and deployment complete.
pause