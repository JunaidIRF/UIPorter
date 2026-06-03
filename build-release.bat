@echo off
setlocal enabledelayedexpansion

echo ============================================================
echo  UIPorter Standalone Build
echo ============================================================

:: ── Locate Maven ─────────────────────────────────────────────
set "MVN="
where mvn.cmd >nul 2>&1 && set "MVN=mvn.cmd"
if not defined MVN where mvn >nul 2>&1 && set "MVN=mvn"

if not defined MVN (
    if defined M2_HOME (
        if exist "%M2_HOME%\bin\mvn.cmd" set "MVN=%M2_HOME%\bin\mvn.cmd"
    )
)
if not defined MVN (
    if defined NETBEANS_HOME (
        if exist "%NETBEANS_HOME%\java\maven\bin\mvn.cmd" set "MVN=%NETBEANS_HOME%\java\maven\bin\mvn.cmd"
    )
)
:: Common NetBeans locations
if not defined MVN if exist "F:\Software\netbeans-28-bin\netbeans\java\maven\bin\mvn.cmd" set "MVN=F:\Software\netbeans-28-bin\netbeans\java\maven\bin\mvn.cmd"
if not defined MVN if exist "C:\Program Files\NetBeans\java\maven\bin\mvn.cmd"            set "MVN=C:\Program Files\NetBeans\java\maven\bin\mvn.cmd"
if not defined MVN if exist "%APPDATA%\..\Local\NetBeans\java\maven\bin\mvn.cmd"          set "MVN=%APPDATA%\..\Local\NetBeans\java\maven\bin\mvn.cmd"

if not defined MVN (
    echo ERROR: Maven ^(mvn^) not found.
    echo Add Maven to your PATH, or set M2_HOME / NETBEANS_HOME.
    exit /b 1
)
echo Found Maven: %MVN%

:: Clean up legacy target\release if present (from older builds) so mvn clean doesn't choke
if exist "target\release" (
    taskkill /f /im UIPorter.exe >nul 2>&1
    timeout /t 1 /nobreak >nul
    rmdir /s /q "target\release" 2>nul
)
cmd /c exit 0

:: ── Locate jpackage ───────────────────────────────────────────
set "JPACKAGE="
where jpackage >nul 2>&1 && set "JPACKAGE=jpackage"
if not defined JPACKAGE (
    if defined JAVA_HOME (
        if exist "%JAVA_HOME%\bin\jpackage.exe" set "JPACKAGE=%JAVA_HOME%\bin\jpackage.exe"
    )
)
if not defined JPACKAGE (
    echo ERROR: jpackage not found. Make sure JDK 14+ is installed and JAVA_HOME is set.
    exit /b 1
)
echo Found jpackage: %JPACKAGE%

:: ── Step 1: Maven build ───────────────────────────────────────
echo.
echo [1/2] Running Maven build...
call "%MVN%" clean package -DskipTests
if %errorlevel% neq 0 (
    echo ERROR: Maven build failed.
    exit /b 1
)

:: ── Step 2: jpackage app-image ────────────────────────────────
echo.
echo [2/3] Creating app-image with jpackage...

if exist "release\UIPorter" (
    rmdir /s /q "release\UIPorter"
)

:: Copy the app JAR into cp/ so --input can find it
copy /Y "target\UIPorter-1.0-SNAPSHOT.jar" "target\libs\cp\" >nul

:: Optional: set --icon if an .ico file is found (jpackage on Windows requires .ico)
set "ICON_ARG="
if exist "src\main\resources\com\jabcodex\uiporter\icons\icon.ico" (
    set "ICON_ARG=--icon src\main\resources\com\jabcodex\uiporter\icons\icon.ico"
)

"%JPACKAGE%" ^
  --type app-image ^
  --name UIPorter ^
  --app-version 1.0 ^
  --input "target\libs\cp" ^
  --main-jar UIPorter-1.0-SNAPSHOT.jar ^
  --main-class com.jabcodex.uiporter.App ^
  --module-path "target\libs\modules" ^
  --add-modules javafx.controls,javafx.fxml,jdk.compiler,java.compiler,java.net.http,jdk.crypto.ec,java.logging ^
  --dest release ^
  !ICON_ARG! ^
  --java-options "-Djavafx.enablePreview=true" ^
  --java-options "--module-path $APPDIR" ^
  --java-options "--add-modules=javafx.controls,javafx.fxml,java.net.http"

if %errorlevel% neq 0 (
    echo ERROR: jpackage failed.
    exit /b 1
)

:: ── Step 3: Add JavaFX JARs for preview subprocess ─────────────
echo.
echo [3/3] Copying JavaFX JARs for preview subprocess...
mkdir "release\UIPorter\app\javafx-mods"
copy /Y "target\libs\modules\*.jar" "release\UIPorter\app\javafx-mods\" >nul
if %errorlevel% neq 0 (
    echo ERROR: Failed to copy JavaFX JARs to javafx-mods.
    exit /b 1
)

echo.
echo ============================================================
echo  Done! Portable app is at: release\UIPorter\
echo  Run:  release\UIPorter\UIPorter.exe
echo ============================================================
endlocal
exit /b 0
