@echo off
chcp 65001 >nul
echo.
echo ╔══════════════════════════════════════════════════════════╗
echo ║        COMPILADOR AUTOMÁTICO DE APK                     ║
echo ╚══════════════════════════════════════════════════════════╝
echo.

REM Verificar se Java está instalado
echo [1/5] Verificando Java...
java -version >nul 2>&1
if errorlevel 1 (
    echo ❌ Java não encontrado!
    echo.
    echo Por favor, instale Java JDK 17:
    echo https://adoptium.net/download/
    echo.
    pause
    exit /b 1
)
echo ✅ Java encontrado!
echo.

REM Verificar se o arquivo local.properties existe
echo [2/5] Verificando configurações...
if not exist "local.properties" (
    echo ⚠️  local.properties não encontrado
    echo.
    set /p SDK_PATH="Digite o caminho do Android SDK (ex: C:\Android-SDK): "
    echo sdk.dir=!SDK_PATH:\=\\! > local.properties
    echo ✅ Arquivo local.properties criado
) else (
    echo ✅ Configurações OK
)
echo.

REM Verificar se gradlew existe
echo [3/5] Verificando Gradle...
if not exist "gradlew.bat" (
    echo ❌ gradlew.bat não encontrado!
    echo Este arquivo deve estar na pasta do projeto Android.
    pause
    exit /b 1
)
echo ✅ Gradle encontrado!
echo.

REM Limpar builds anteriores
echo [4/5] Limpando builds anteriores...
if exist "app\build\outputs\apk" (
    rmdir /s /q "app\build\outputs\apk" 2>nul
)
echo ✅ Limpeza concluída
echo.

REM Compilar APK
echo [5/5] Compilando APK...
echo.
echo ══════════════════════════════════════════════════════════
echo Isso pode demorar alguns minutos na primeira vez...
echo ══════════════════════════════════════════════════════════
echo.

call gradlew.bat assembleDebug

if errorlevel 1 (
    echo.
    echo ══════════════════════════════════════════════════════════
    echo ❌ ERRO na compilação!
    echo ══════════════════════════════════════════════════════════
    echo.
    echo Possíveis causas:
    echo  - Android SDK não configurado corretamente
    echo  - Falta de espaço em disco
    echo  - Problemas de conexão (Gradle precisa baixar dependências)
    echo.
    echo Tente:
    echo  1. Verificar se o Android SDK está instalado
    echo  2. Verificar o arquivo local.properties
    echo  3. Executar: gradlew.bat --stacktrace
    echo.
    pause
    exit /b 1
)

echo.
echo ══════════════════════════════════════════════════════════
echo ✅ COMPILAÇÃO CONCLUÍDA COM SUCESSO!
echo ══════════════════════════════════════════════════════════
echo.
echo 📦 APK gerado em:
echo    app\build\outputs\apk\debug\app-debug.apk
echo.
echo 📱 Para instalar no Android:
echo.
echo   OPÇÃO 1 - Via USB (com ADB):
echo     adb install app\build\outputs\apk\debug\app-debug.apk
echo.
echo   OPÇÃO 2 - Transferência manual:
echo     1. Copie o APK para o celular
echo     2. Abra o arquivo no celular
echo     3. Permita "Instalar de fontes desconhecidas"
echo     4. Instale normalmente
echo.
echo   OPÇÃO 3 - Via servidor web:
echo     python -m http.server 8000
echo     Acesse no celular: http://seu-ip:8000
echo.
echo ══════════════════════════════════════════════════════════

REM Perguntar se quer abrir a pasta
set /p ABRIR="Deseja abrir a pasta do APK? (S/N): "
if /i "%ABRIR%"=="S" (
    explorer "app\build\outputs\apk\debug"
)

echo.
echo Pressione qualquer tecla para sair...
pause >nul

