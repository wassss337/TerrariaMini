@echo off
cd /d "%~dp0"
del /q *.class 2>nul
del /q TerrariaMini.jar 2>nul
javac TerrariaMini.java
if errorlevel 1 (echo ОШИБКА КОМПИЛЯЦИИ & pause & exit /b 1)
(echo Main-Class: TerrariaMini)> m.txt
jar cfm TerrariaMini.jar m.txt *.class
del /q *.class m.txt
echo ГОТОВО: TerrariaMini.jar
pause