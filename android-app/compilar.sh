#!/bin/bash

echo ""
echo "╔══════════════════════════════════════════════════════════╗"
echo "║        COMPILADOR AUTOMÁTICO DE APK                     ║"
echo "╚══════════════════════════════════════════════════════════╝"
echo ""

# Cores
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Função para erro
function erro {
    echo -e "${RED}❌ $1${NC}"
    exit 1
}

# Função para sucesso
function sucesso {
    echo -e "${GREEN}✅ $1${NC}"
}

# Função para aviso
function aviso {
    echo -e "${YELLOW}⚠️  $1${NC}"
}

# [1/5] Verificar Java
echo "[1/5] Verificando Java..."
if ! command -v java &> /dev/null; then
    erro "Java não encontrado! Instale Java JDK 17"
fi
sucesso "Java encontrado!"
echo ""

# [2/5] Verificar local.properties
echo "[2/5] Verificando configurações..."
if [ ! -f "local.properties" ]; then
    aviso "local.properties não encontrado"
    echo ""
    read -p "Digite o caminho do Android SDK (ex: $HOME/Android-SDK): " SDK_PATH
    echo "sdk.dir=$SDK_PATH" > local.properties
    sucesso "Arquivo local.properties criado"
else
    sucesso "Configurações OK"
fi
echo ""

# [3/5] Verificar gradlew
echo "[3/5] Verificando Gradle..."
if [ ! -f "gradlew" ]; then
    erro "gradlew não encontrado! Este arquivo deve estar na pasta do projeto Android."
fi

# Dar permissão de execução
chmod +x gradlew
sucesso "Gradle encontrado!"
echo ""

# [4/5] Limpar builds anteriores
echo "[4/5] Limpando builds anteriores..."
if [ -d "app/build/outputs/apk" ]; then
    rm -rf app/build/outputs/apk 2>/dev/null
fi
sucesso "Limpeza concluída"
echo ""

# [5/5] Compilar APK
echo "[5/5] Compilando APK..."
echo ""
echo "══════════════════════════════════════════════════════════"
echo "Isso pode demorar alguns minutos na primeira vez..."
echo "══════════════════════════════════════════════════════════"
echo ""

./gradlew assembleDebug

if [ $? -ne 0 ]; then
    echo ""
    echo "══════════════════════════════════════════════════════════"
    erro "ERRO na compilação!"
    echo "══════════════════════════════════════════════════════════"
    echo ""
    echo "Possíveis causas:"
    echo "  - Android SDK não configurado corretamente"
    echo "  - Falta de espaço em disco"
    echo "  - Problemas de conexão (Gradle precisa baixar dependências)"
    echo ""
    echo "Tente:"
    echo "  1. Verificar se o Android SDK está instalado"
    echo "  2. Verificar o arquivo local.properties"
    echo "  3. Executar: ./gradlew --stacktrace"
    echo ""
    exit 1
fi

echo ""
echo "══════════════════════════════════════════════════════════"
sucesso "COMPILAÇÃO CONCLUÍDA COM SUCESSO!"
echo "══════════════════════════════════════════════════════════"
echo ""
echo "📦 APK gerado em:"
echo "   app/build/outputs/apk/debug/app-debug.apk"
echo ""
echo "📱 Para instalar no Android:"
echo ""
echo "  OPÇÃO 1 - Via USB (com ADB):"
echo "    adb install app/build/outputs/apk/debug/app-debug.apk"
echo ""
echo "  OPÇÃO 2 - Transferência manual:"
echo "    1. Copie o APK para o celular"
echo "    2. Abra o arquivo no celular"
echo "    3. Permita \"Instalar de fontes desconhecidas\""
echo "    4. Instale normalmente"
echo ""
echo "  OPÇÃO 3 - Via servidor web:"
echo "    python3 -m http.server 8000"
echo "    Acesse no celular: http://seu-ip:8000"
echo ""
echo "══════════════════════════════════════════════════════════"
echo ""

# Perguntar se quer abrir a pasta
read -p "Deseja abrir a pasta do APK? (s/n): " ABRIR
if [[ "$ABRIR" =~ ^[Ss]$ ]]; then
    if command -v xdg-open &> /dev/null; then
        xdg-open "app/build/outputs/apk/debug"
    elif command -v open &> /dev/null; then
        open "app/build/outputs/apk/debug"
    else
        echo "Abra manualmente: app/build/outputs/apk/debug"
    fi
fi

echo ""
echo "Pressione Enter para sair..."
read

