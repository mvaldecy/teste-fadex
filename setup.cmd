@echo off
rem Atalho para quem esta no Windows e prefere dar duplo clique.
rem Localiza o bash do Git for Windows (ou do WSL) e roda o setup.sh.
setlocal

cd /d "%~dp0"

set "BASH_EXE="
if exist "%ProgramFiles%\Git\bin\bash.exe" set "BASH_EXE=%ProgramFiles%\Git\bin\bash.exe"
if not defined BASH_EXE if exist "%ProgramFiles(x86)%\Git\bin\bash.exe" set "BASH_EXE=%ProgramFiles(x86)%\Git\bin\bash.exe"
if not defined BASH_EXE if exist "%LOCALAPPDATA%\Programs\Git\bin\bash.exe" set "BASH_EXE=%LOCALAPPDATA%\Programs\Git\bin\bash.exe"

if defined BASH_EXE (
  "%BASH_EXE%" ./setup.sh %*
  goto fim
)

where wsl >nul 2>&1
if %ERRORLEVEL%==0 (
  wsl ./setup.sh %*
  goto fim
)

echo.
echo Nao encontrei o bash. Instale o Git for Windows (https://git-scm.com/download/win)
echo ou o WSL, e depois rode:  ./setup.sh
echo.

:fim
pause
endlocal
