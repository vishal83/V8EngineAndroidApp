#!/bin/bash
#
# QuickJS Build Script for Android
# This script builds QuickJS for multiple Android architectures
#

set -e

# Configuration
QUICKJS_DIR="app/src/main/cpp/quickjs/quickjs-2025-04-26"
ANDROID_NDK_ROOT=${ANDROID_NDK_ROOT:-"$HOME/Android/Sdk/ndk/26.1.10909125"}
API_LEVEL=24

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

echo -e "${GREEN}🚀 Building QuickJS for Android${NC}"
echo "NDK Root: $ANDROID_NDK_ROOT"
echo "API Level: $API_LEVEL"
echo "QuickJS Version: 2025-04-26"
echo ""

if [ ! -d "$ANDROID_NDK_ROOT" ]; then
    echo -e "${RED}❌ Android NDK not found at: $ANDROID_NDK_ROOT${NC}"
    echo "Please set ANDROID_NDK_ROOT environment variable"
    exit 1
fi

if [ ! -d "$QUICKJS_DIR" ]; then
    echo -e "${RED}❌ QuickJS source not found at: $QUICKJS_DIR${NC}"
    exit 1
fi

cd "$QUICKJS_DIR"

# Build for different architectures
ARCHITECTURES=("arm64-v8a" "armeabi-v7a" "x86_64")

for ARCH in "${ARCHITECTURES[@]}"; do
    echo -e "${YELLOW}📱 Building for architecture: $ARCH${NC}"
    
    case $ARCH in
        arm64-v8a)
            TARGET="aarch64-linux-android"
            ;;
        armeabi-v7a)
            TARGET="armv7a-linux-androideabi"
            ;;
        x86_64)
            TARGET="x86_64-linux-android"
            ;;
    esac
    
    # Set up cross-compilation environment
    export CC="$ANDROID_NDK_ROOT/toolchains/llvm/prebuilt/darwin-x86_64/bin/${TARGET}${API_LEVEL}-clang"
    export AR="$ANDROID_NDK_ROOT/toolchains/llvm/prebuilt/darwin-x86_64/bin/llvm-ar"
    export CFLAGS="-DCONFIG_ANDROID=1 -D_GNU_SOURCE -DCONFIG_BIGNUM -DCONFIG_ATOMICS -O3 -flto"
    
    # Clean previous build
    make clean > /dev/null 2>&1 || true
    
    # Build QuickJS library
    echo "  🔨 Compiling..."
    if make libquickjs.a > build_${ARCH}.log 2>&1; then
        echo -e "  ✅ ${GREEN}Build successful for $ARCH${NC}"
        
        # Create architecture-specific lib directory
        mkdir -p "../lib/$ARCH"
        cp libquickjs.a "../lib/$ARCH/"
        
        # Show library info
        SIZE=$(stat -f%z libquickjs.a 2>/dev/null || stat -c%s libquickjs.a 2>/dev/null || echo "unknown")
        echo "  📦 Library size: ${SIZE} bytes"
    else
        echo -e "  ❌ ${RED}Build failed for $ARCH${NC}"
        echo "  📄 Check build_${ARCH}.log for details"
    fi
    echo ""
done

echo -e "${GREEN}✨ QuickJS build completed!${NC}"
echo ""
echo "📁 Libraries created in:"
for ARCH in "${ARCHITECTURES[@]}"; do
    if [ -f "../lib/$ARCH/libquickjs.a" ]; then
        echo -e "  ✅ ${GREEN}../lib/$ARCH/libquickjs.a${NC}"
    else
        echo -e "  ❌ ${RED}../lib/$ARCH/libquickjs.a (missing)${NC}"
    fi
done

echo ""
echo -e "${YELLOW}📝 Next steps:${NC}"
echo "1. Update CMakeLists.txt to link against the static libraries"
echo "2. Build your Android project normally"
echo "3. Test QuickJS integration in your app"

cd - > /dev/null
