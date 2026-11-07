@echo off
cd /d "%~dp0"
echo === Сборка TerrariaMini.jar ===

rem чистим старое
del /q /s terrariamini\*.class 2>nul
del /q TerrariaMini.jar 2>nul
del /q m.txt 2>nul

rem компилируем ВСЁ из пакета
javac terrariamini\*.java
if errorlevel 1 (echo ОШИБКА КОМПИЛЯЦИИ & pause & exit /b 1)

rem манифест — Main-Class С ПАКЕТОМ
(echo Main-Class: terrariamini.TerrariaMini)> m.txt

rem собираем jar с сохранением пути пакета
jar cfm TerrariaMini.jar m.txt -C . terrariamini
if errorlevel 1 (echo ОШИБКА JAR & del /q m.txt 2>nul & pause & exit /b 1)

del /q m.txt
del /q /s terrariamini\*.class 2>nul
echo ГОТОВО: TerrariaMini.jar
pause