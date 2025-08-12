# V8 Libraries for Android

Place the following V8 library files in their respective ABI directories:

## arm64-v8a/
- libv8_monolith.a

## armeabi-v7a/
- libv8_monolith.a  

## x86_64/
- libv8_monolith.a

## How to get V8 binaries:

### Option 1: Build from source (recommended)
```bash
# Install depot_tools
git clone https://chromium.googlesource.com/chromium/tools/depot_tools.git
export PATH=$PATH:/path/to/depot_tools

# Fetch V8
fetch v8
cd v8

# Build for Android
gn gen out/android_arm64 --args='target_os="android" target_cpu="arm64" v8_target_cpu="arm64" android_ndk_root="/path/to/ndk" v8_static_library=true is_component_build=false v8_monolithic=true'
ninja -C out/android_arm64 v8_monolith
```

### Option 2: Use J2V8 binaries
You can extract V8 binaries from J2V8 releases:
https://github.com/eclipsesource/J2V8/releases

### Option 3: NodeJS Android build
Extract V8 from NodeJS Android builds:
https://nodejs.org/dist/
