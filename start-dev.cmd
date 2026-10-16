@echo off
rem Starts the Spring Boot API (port 8080) and the React dev server (port 5173), each in its own window.
set ROOT=%~dp0

start "CodeTutor API (8080)" cmd /k "cd /d %ROOT%backend && mvn spring-boot:run"

if not exist "%ROOT%frontend\node_modules" (
  echo Installing front-end packages...
  pushd "%ROOT%frontend" && call npm install && popd
)
start "CodeTutor UI (5173)" cmd /k "cd /d %ROOT%frontend && npm run dev"

echo.
echo Both servers are starting. Open http://localhost:5173 in your browser in about 20 seconds.
