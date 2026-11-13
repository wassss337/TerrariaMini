@echo off
setlocal enabledelayedexpansion
cd /d "%~dp0"

echo ============================================
echo   Сборка TerrariaMini.jar
echo ============================================
echo.

rem ---- проверка javac ----
where javac >nul 2>nul
if errorlevel 1 (
    echo [ОШИБКА] javac не найден в PATH.
    echo Установи JDK 11+ и добавь bin в PATH.
    pause
    exit /b 1
)

for /f "tokens=3" %%v in ('java -version 2^>^&1 ^| findstr /i "version"') do set JAVA_VER=%%v
echo Java: !JAVA_VER!
echo.

rem ---- чистим старое ----
echo Чистка...
del /q /s terrariamini\*.class 2>nul
del /q TerrariaMini.jar 2>nul
del /q m.txt 2>nul
echo.

rem ---- список исходников ----
set SRC_LIST=
for /r terrariamini %%f in (*.java) do set SRC_LIST=!SRC_LIST! "%%f"
if "!SRC_LIST!"=="" (
    echo [ОШИБКА] Не найдено ни одного .java в terrariamini\
    pause
    exit /b 1
)

rem ---- компиляция ----
echo Компиляция...
javac -encoding UTF-8 -d . terrariamini\*.java
if errorlevel 1 (
    echo.
    echo [ОШИБКА] Компиляция не удалась.
    pause
    exit /b 1
)
echo Компиляция OK.
echo.

rem ---- манифест ----
(echo Main-Class: terrariamini.TerrariaMini)> m.txt

rem ---- сборка jar ----
echo Сборка jar...
jar cfm TerrariaMini.jar m.txt -C . terrariamini
if errorlevel 1 (
    echo [ОШИБКА] Сборка jar не удалась.
    del /q m.txt 2>nul
    pause
    exit /b 1
)
del /q m.txt
del /q /s terrariamini\*.class 2>nul

echo.
echo ============================================
echo   ГОТОВО: TerrariaMini.jar
echo ============================================
dir /b TerrariaMini.jar
pause
endlocal