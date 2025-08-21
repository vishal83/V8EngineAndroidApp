# 🚀 JavaScript Server Setup for Remote Execution

This guide helps you set up a web server to host JavaScript files for testing the remote execution feature in your QuickJS Android app.

## 📁 Files Created

- `test_remote_script.js` - Comprehensive test JavaScript file
- `js_server.js` - Node.js server with CORS support
- `README_SERVER.md` - This guide

## 🖥️ Local Server Options

### Option 1: Python HTTP Server (Quick & Simple)

```bash
# Start Python server (port 8000)
python3 -m http.server 8000

# Access your file at:
# http://localhost:8000/test_remote_script.js
```

**Pros**: No installation needed, works immediately  
**Cons**: No CORS headers, basic functionality

### Option 2: Node.js Server (Recommended)

```bash
# Install Node.js if not installed
brew install node  # macOS
# or download from https://nodejs.org

# Start the server
node js_server.js

# Server runs on port 8080 with features:
# - CORS headers for mobile compatibility
# - Web interface at http://localhost:8080
# - API endpoint at http://localhost:8080/api/files
# - Detailed logging
```

**Pros**: CORS support, better error handling, web interface  
**Cons**: Requires Node.js installation

## 📱 Testing with Android App

### Step 1: Find Your Computer's IP Address

```bash
# macOS/Linux
ifconfig | grep "inet " | grep -v 127.0.0.1

# Windows
ipconfig | findstr "IPv4"

# Example output: 192.168.1.100
```

### Step 2: Use IP Address in App

In your Android app's "Remote JS" tab, use:
- **Python server**: `http://192.168.1.100:8000/test_remote_script.js`
- **Node.js server**: `http://192.168.1.100:8080/test_remote_script.js`

Replace `192.168.1.100` with your actual IP address.

### Step 3: Test Connectivity

Make sure your phone and computer are on the same WiFi network, then test the URL in your app.

## 🌐 Online Hosting Options

### Option 1: GitHub Pages (Free)

1. Create a new repository or use existing one
2. Upload `test_remote_script.js` to the repository
3. Enable GitHub Pages in repository settings
4. Access at: `https://username.github.io/repository/test_remote_script.js`

### Option 2: Netlify Drop (Free)

1. Go to [netlify.com/drop](https://netlify.com/drop)
2. Drag and drop your `test_remote_script.js` file
3. Get instant URL like: `https://random-name.netlify.app/test_remote_script.js`

### Option 3: JSFiddle/CodePen

1. Paste your JavaScript code
2. Use the raw URL in your app
3. Example: `https://jsfiddle.net/username/fiddle-id/latest/js/`

## 🧪 Test JavaScript Examples

### Simple Test Script
```javascript
// Simple test for QuickJS
const result = {
    message: "Hello from remote server!",
    timestamp: new Date().toISOString(),
    calculation: 2 + 3 * 4,
    array: [1,2,3].map(x => x * 2)
};
JSON.stringify(result, null, 2);
```

### Library Loading Test
Try these popular JavaScript libraries:
- **Lodash**: `https://cdn.jsdelivr.net/npm/lodash@4.17.21/lodash.min.js`
- **Moment.js**: `https://cdn.jsdelivr.net/npm/moment@2.29.4/moment.min.js`
- **Math.js**: `https://cdn.jsdelivr.net/npm/mathjs@11.11.0/lib/browser/math.min.js`

## 🔧 Troubleshooting

### "Network error" in app
- Check if server is running
- Verify IP address is correct
- Ensure phone and computer are on same network
- Try accessing URL in phone's browser first

### "CORS error"
- Use the Node.js server (`js_server.js`) which includes CORS headers
- Or add CORS headers to your hosting solution

### "Content not JavaScript"
- Ensure file extension is `.js`
- Check server is sending correct `Content-Type: text/javascript` header

### Connection timeout
- Check firewall settings
- Ensure port is not blocked
- Try different port numbers

## 📊 Server Comparison

| Feature | Python Server | Node.js Server | GitHub Pages |
|---------|---------------|----------------|--------------|
| Setup | Instant | Need Node.js | Need GitHub account |
| CORS | ❌ | ✅ | ✅ |
| Logging | Basic | Detailed | None |
| SSL/HTTPS | ❌ | ❌ | ✅ |
| Public Access | ❌ | ❌ | ✅ |
| Cost | Free | Free | Free |

## 🎯 Recommended Workflow

1. **Development**: Use Node.js server (`js_server.js`)
2. **Testing**: Use Python server for quick tests
3. **Production**: Use GitHub Pages or Netlify for permanent hosting

## 📞 Support

If you encounter issues:
1. Check the server logs for error messages
2. Test the URL in a web browser first
3. Verify network connectivity
4. Try different JavaScript files to isolate issues

Happy remote JavaScript execution! 🚀
