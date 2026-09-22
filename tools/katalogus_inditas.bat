@echo off
chcp 65001 >nul
title Vox Libris - katalogus keszites

rem A gepen tobb Python is van, es a "python" szo a torott C:\Python313-ra
rem mutat. Ezert irjuk ki pontosan, melyiket hasznaljuk.
set PY=C:\Users\Sanyi\AppData\Local\Programs\Python\Python312\python.exe

rem A konyvek mappaja. Ha maskor mas mappat dolgoznal fel, ezt az egy sort
rem kell atirni - semmi mast.
set KONYVEK=d:\______XXXX_Könyvek\Telefon tartalma\_____Text könyvek

echo.
echo   Vox Libris - katalogus keszites Geminivel
echo   =========================================
echo.
echo   Mappa: %KONYVEK%
echo.
echo   Ez hosszu munka. Ha elfogy a napi keret, megvarja a megujulasat
echo   es folytatja. Barmikor bezarhatod - legkozelebb ott folytatja.
echo.

"%PY%" -u "%~dp0vox_gemini.py" "%KONYVEK%" --mod katalogus --kitart

echo.
echo   A futas veget ert. Nyomj egy billentyut a bezarashoz.
pause >nul
