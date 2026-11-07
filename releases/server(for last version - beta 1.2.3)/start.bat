@echo off
cd /d "%~dp0"
title TerrariaMini Server
:loop
echo [%date% %time%] Запуск сервера...
java -Xmx1G -jar TerrariaMiniServer.jar
echo [%date% %time%] Сервер остановлен. Перезапуск через 5 сек...
timeout /t 5
goto loop