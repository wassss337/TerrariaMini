@echo off
javac TerrariaMini.java
if errorlevel 1 (
    echo ОШИБКА КОМПИЛЯЦИИ
    pause
    exit /b 1
)
(echo Main-Class: TerrariaMini)> manifest.txt
jar cfm TerrariaMini.jar manifest.txt *.class
if errorlevel 1 (
    echo ОШИБКА JAR
    del /q *.class manifest.txt 2>nul
    pause
    exit /b 1
)
del /q *.class manifest.txt
echo ГОТОВО: TerrariaMini.jar
pause