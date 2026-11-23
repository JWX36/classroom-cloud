@echo off
chcp 65001 >nul
cd /d "%~dp0"
echo ================================================
echo   课业云 · 启动（开发环境，端口 8081）
echo   学生 20240101/123456  教师 HS010001/123456
echo   管理员 admin/admin123
echo ================================================
if exist "C:\Users\JWX\.jdks\openjdk-25\bin\java.exe" (
  set "JAVA_HOME=C:\Users\JWX\.jdks\openjdk-25"
  set "PATH=%JAVA_HOME%\bin;%PATH%"
) else (
  echo [警告] 未找到 JDK 25，尝试使用系统 java...
)
if not exist "target\demo-0.0.1-SNAPSHOT.jar" (
  echo [提示] 未找到 jar 包，先执行 Maven 打包（首次约 1 分钟）...
  call mvnw.cmd -q -o package -DskipTests
)
java -jar "target\demo-0.0.1-SNAPSHOT.jar" --spring.profiles.active=dev
pause
