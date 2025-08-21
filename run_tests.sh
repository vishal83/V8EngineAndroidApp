#!/bin/bash

# QuickJS Android App Test Runner
# This script runs all tests for the QuickJS Android application

set -e

echo "🧪 QuickJS Android App Test Suite"
echo "================================="

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Function to print colored output
print_status() {
    echo -e "${BLUE}[INFO]${NC} $1"
}

print_success() {
    echo -e "${GREEN}[SUCCESS]${NC} $1"
}

print_warning() {
    echo -e "${YELLOW}[WARNING]${NC} $1"
}

print_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# Check if Android SDK is available
if ! command -v adb &> /dev/null; then
    print_error "ADB not found. Please ensure Android SDK is installed and in PATH."
    exit 1
fi

# Check if Gradle wrapper exists
if [ ! -f "./gradlew" ]; then
    print_error "gradlew not found. Please run this script from the project root."
    exit 1
fi

print_status "Starting test execution..."

# Run unit tests
print_status "Running unit tests..."
if ./gradlew test; then
    print_success "Unit tests passed!"
else
    print_error "Unit tests failed!"
    exit 1
fi

# Check if device/emulator is connected
if ! adb devices | grep -q "device"; then
    print_warning "No Android device/emulator detected."
    print_status "Please connect a device or start an emulator to run integration tests."
    print_status "Skipping integration tests..."
else
    print_status "Android device/emulator detected. Running integration tests..."
    
    # Run integration tests
    if ./gradlew connectedAndroidTest; then
        print_success "Integration tests passed!"
    else
        print_error "Integration tests failed!"
        exit 1
    fi
fi

# Generate test reports
print_status "Generating test reports..."
./gradlew testDebugUnitTestCoverage || true

# Display results summary
echo ""
echo "📊 Test Results Summary"
echo "======================"

# Check for test reports
if [ -d "app/build/reports/tests/testDebugUnitTest" ]; then
    print_success "Unit test reports: app/build/reports/tests/testDebugUnitTest/index.html"
fi

if [ -d "app/build/reports/androidTests/connected" ]; then
    print_success "Integration test reports: app/build/reports/androidTests/connected/index.html"
fi

if [ -d "app/build/reports/coverage/testDebugUnitTestCoverage" ]; then
    print_success "Coverage reports: app/build/reports/coverage/testDebugUnitTestCoverage/html/index.html"
fi

print_success "All tests completed successfully! 🎉"

echo ""
echo "📋 Test Categories Covered:"
echo "• CacheService unit tests - Memory/disk caching, expiration, stats"
echo "• HttpService unit tests - HTTP requests, conditional caching, error handling"
echo "• QuickJSBridge unit tests - JavaScript execution, context management"
echo "• Integration tests - End-to-end workflows, cache performance"
echo "• Performance tests - Cache speed, concurrent access, large files"

echo ""
echo "🔧 To run specific test categories:"
echo "  Unit tests only:        ./gradlew test"
echo "  Integration tests only: ./gradlew connectedAndroidTest"
echo "  Specific test class:    ./gradlew test --tests CacheServiceTest"
echo "  Performance tests:      ./gradlew connectedAndroidTest --tests CachePerformanceTest"

echo ""
echo "📖 For more details, check the generated HTML reports above."
