@echo off
chcp 65001 >nul
cd /d "%~dp0"
if exist .venv\Scripts\pythonw.exe goto run
set PY=
py -3.12 -c "1" >nul 2>nul && set PY=py -3.12
if not defined PY py -3.11 -c "1" >nul 2>nul && set PY=py -3.11
if not defined PY py -3.13 -c "1" >nul 2>nul && set PY=py -3.13
if not defined PY python -c "1" >nul 2>nul && set PY=python
if not defined PY goto nopython
echo Preparando la herramienta por primera vez (unos minutos)...
%PY% -m venv .venv
if errorlevel 1 goto fail
.venv\Scripts\python -m pip install --upgrade pip
.venv\Scripts\python -m pip install -r requirements.txt
if errorlevel 1 goto fail
:run
start "" .venv\Scripts\pythonw.exe video_a_baile.py
exit /b 0
:nopython
echo No se encontro Python. Instala Python 3.12 desde python.org marcando "Add Python to PATH".
pause
exit /b 1
:fail
echo Algo fallo al instalar. Borra la carpeta .venv y vuelve a intentarlo.
rmdir /s /q .venv
pause
exit /b 1
