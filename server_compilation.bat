@echo off
cd /d "%~dp0"
del /q *.class 2>nul
del /q TerrariaMiniServer.jar 2>nul
javac TerrariaMini.java ServerMain.java
if errorlevel 1 (echo ОШИБКА КОМПИЛЯЦИИ & pause & exit /b 1)
(echo Main-Class: ServerMain)> m.txt
jar cfm TerrariaMiniServer.jar m.txt *.class
del /q *.class m.txt
echo ГОТОВО: TerrariaMiniServer.jar
pause