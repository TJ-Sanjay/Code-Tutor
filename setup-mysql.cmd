@echo off
rem One-time setup: creates the "codetutor" MySQL database and user.
rem You will be asked for your MySQL root password - type it here, it is not stored anywhere.
set MYSQL="C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe"
if not exist %MYSQL% (
  echo Could not find mysql.exe. Edit this file and set the MYSQL path, or run backend\db\setup-mysql.sql in MySQL Workbench.
  pause
  exit /b 1
)
echo.
echo Creating the CodeTutor database in MySQL...
%MYSQL% -u root -p < "%~dp0backend\db\setup-mysql.sql"
if errorlevel 1 (
  echo.
  echo FAILED - check the root password and that the MySQL service is running.
) else (
  echo.
  echo Done. Now run start-dev.cmd. Your existing data is copied into MySQL automatically on the first start.
)
pause
