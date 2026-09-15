#!/bin/bash

# Color codes for readable terminal output
GREEN='\033[0;32m'
BLUE='\033[0;34m'
RED='\033[0;31m'
NC='\033[0m' # No Color

echo -e "${BLUE}=== RetroSnake Build System ===${NC}"

# 1. Prepare directories
echo "Checking directories..."
mkdir -p bin libs builds

# 2. Compile source code
echo "Compiling Java source files..."
javac -d bin src/*.java

if [ $? -ne 0 ]; then
    echo -e "${RED}Compilation failed! Check your syntax.${NC}"
    exit 1
fi

# 3. Package the JAR file
echo "Packaging RetroSnake.jar..."
jar cfe libs/RetroSnake.jar SnakeApp -C bin .
echo -e "${GREEN}SUCCESS: libs/RetroSnake.jar created.${NC}"

# 4. Optional Native Builds
if [ "$1" == "--native" ]; then
    echo -e "${BLUE}=== Starting Native Builds ===${NC}"
    
    rm -rf builds/linux builds/windows
    mkdir -p builds/linux builds/windows

    # Build Linux App-Image
    echo "Building Linux native app..."
    jpackage --type app-image --name RetroSnake --input libs --main-jar RetroSnake.jar --main-class SnakeApp --dest builds/linux
    echo -e "${GREEN}Linux build complete.${NC}"

    # Build Windows Executable via Wine
    echo "Preparing Windows build environment..."
    if [ ! -d "jdk-21.0.2" ]; then
        echo "Downloading Windows JDK..."
        wget -q https://download.java.net/java/GA/jdk21.0.2/f2283984656d49d69e91c558476027ac/13/GPL/openjdk-21.0.2_windows-x64_bin.zip
        unzip -q openjdk-21.0.2_windows-x64_bin.zip
        rm openjdk-21.0.2_windows-x64_bin.zip
    fi

    echo "Building Windows native app via Wine C: drive workaround..."
    
    # Create a temporary workspace inside Wine's C: drive
    WINE_TEMP="$HOME/.wine/drive_c/RetroSnakeTemp"
    rm -rf "$WINE_TEMP"
    mkdir -p "$WINE_TEMP/libs"
    
    # Copy the JAR into the virtual C: drive
    cp libs/RetroSnake.jar "$WINE_TEMP/libs/"
    
    # Run jpackage strictly inside the C: drive to bypass the Z: drive bug
    wine jdk-21.0.2/bin/jpackage.exe --type app-image --name RetroSnake --input 'C:\RetroSnakeTemp\libs' --main-jar RetroSnake.jar --main-class SnakeApp --dest 'C:\RetroSnakeTemp\out'
    
    # Move the built Windows folder back to our local project
    mv "$WINE_TEMP/out/RetroSnake" builds/windows/
    
    # Clean up the virtual C: drive
    rm -rf "$WINE_TEMP"
    
    echo -e "${GREEN}Windows build complete. Check the builds/windows/ folder.${NC}"
fi

echo -e "${BLUE}=== Build Process Finished ===${NC}"
