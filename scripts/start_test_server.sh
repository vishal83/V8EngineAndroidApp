#!/bin/bash

# Start Test Server for QuickJS Android App
# This script starts a Python HTTP server to serve JavaScript test files

echo "🌐 Starting Test Server for QuickJS Android App"
echo "=============================================="

# Get the script directory and project root
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"
TEST_SERVER_DIR="$PROJECT_ROOT/test-server"

# Check if test-server directory exists
if [ ! -d "$TEST_SERVER_DIR" ]; then
    echo "❌ Error: test-server directory not found at $TEST_SERVER_DIR"
    exit 1
fi

# Get local IP address
if command -v ipconfig >/dev/null 2>&1; then
    # macOS
    LOCAL_IP=$(ipconfig getifaddr en0 2>/dev/null || ipconfig getifaddr en1 2>/dev/null || echo "127.0.0.1")
elif command -v ip >/dev/null 2>&1; then
    # Linux
    LOCAL_IP=$(ip route get 1.1.1.1 | grep -oP 'src \K\S+' 2>/dev/null || echo "127.0.0.1")
else
    LOCAL_IP="127.0.0.1"
fi

PORT=8000

echo ""
echo "📁 Serving files from: $TEST_SERVER_DIR"
echo "🌍 Server will be available at:"
echo "   - Local:    http://127.0.0.1:$PORT"
echo "   - Network:  http://$LOCAL_IP:$PORT"
echo ""
echo "📱 For Android testing, use: $LOCAL_IP:$PORT"
echo ""
echo "📋 Available test files:"
echo "   - test_cache_system_fast.js  (Fast caching test)"
echo "   - test_remote_script.js      (Basic remote execution)"
echo "   - test_fetch_polyfill.js     (HTTP polyfills test)"
echo ""
echo "🚀 Starting server... (Press Ctrl+C to stop)"
echo ""

# Change to test-server directory and start Python HTTP server
cd "$TEST_SERVER_DIR"

# Try Python 3 first, then Python 2
if command -v python3 >/dev/null 2>&1; then
    python3 -m http.server $PORT
elif command -v python >/dev/null 2>&1; then
    python -m SimpleHTTPServer $PORT
else
    echo "❌ Error: Python not found. Please install Python to run the test server."
    exit 1
fi
