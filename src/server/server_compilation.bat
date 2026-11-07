@echo off
cd /d "%~dp0"
echo === Сборка TerrariaMiniServer.jar ===

rem чистим
if exist build rmdir /s /q build
mkdir build
del /q TerrariaMiniServer.jar 2>nul
del /q m.txt 2>nul

rem компилируем ВСЁ из пакета terrariamini (включая ServerMain.java)
javac -d build terrariamini\*.java
if errorlevel 1 (echo ОШИБКА КОМПИЛЯЦИИ & pause & exit /b 1)

rem манифест — Main-Class С ПАКЕТОМ
(echo Main-Class: terrariamini.ServerMain)> m.txt

rem собираем jar: берём папку terrariamini из build\
jar cfm TerrariaMiniServer.jar m.txt -C build terrariamini
if errorlevel 1 (echo ОШИБКА JAR & del /q m.txt 2>nul & pause & exit /b 1)

del /q m.txt
rmdir /s /q build

rem переносим jar в корень проекта (рядом с mods\, saves\, datapacks\)
move /y TerrariaMiniServer.jar ..\..\ >nul

echo ГОТОВО: C:\TerrariaMini\TerrariaMiniServer.jar
pause