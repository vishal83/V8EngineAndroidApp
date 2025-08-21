// Test script for fetch() and XMLHttpRequest polyfills
// This script tests the HTTP polyfills implemented in QuickJS

console.log("🌐 Testing HTTP Polyfills in QuickJS");

// Test 1: Check if fetch is available
console.log("1. Testing fetch() availability...");
if (typeof fetch === 'function') {
    console.log("✅ fetch() is available");
} else {
    console.log("❌ fetch() is not available");
}

// Test 2: Check if XMLHttpRequest is available
console.log("2. Testing XMLHttpRequest availability...");
if (typeof XMLHttpRequest === 'function') {
    console.log("✅ XMLHttpRequest is available");
    
    // Test XMLHttpRequest constants
    console.log("   - UNSENT:", XMLHttpRequest.UNSENT);
    console.log("   - OPENED:", XMLHttpRequest.OPENED);
    console.log("   - HEADERS_RECEIVED:", XMLHttpRequest.HEADERS_RECEIVED);
    console.log("   - LOADING:", XMLHttpRequest.LOADING);
    console.log("   - DONE:", XMLHttpRequest.DONE);
} else {
    console.log("❌ XMLHttpRequest is not available");
}

// Test 3: Simple fetch test with JSONPlaceholder
console.log("3. Testing fetch() with HTTP request...");
try {
    fetch('https://jsonplaceholder.typicode.com/posts/1')
        .then(response => {
            console.log("✅ Fetch response received");
            console.log("   - Status:", response.status);
            console.log("   - OK:", response.ok);
            console.log("   - URL:", response.url);
            return response.json();
        })
        .then(data => {
            console.log("✅ JSON data parsed");
            console.log("   - Title:", data.title);
            console.log("   - User ID:", data.userId);
        })
        .catch(error => {
            console.log("❌ Fetch error:", error.message);
        });
} catch (e) {
    console.log("❌ Fetch test failed:", e.message);
}

// Test 4: XMLHttpRequest test
console.log("4. Testing XMLHttpRequest...");
try {
    const xhr = new XMLHttpRequest();
    
    xhr.onreadystatechange = function() {
        console.log("   - ReadyState changed to:", xhr.readyState);
        
        if (xhr.readyState === XMLHttpRequest.DONE) {
            if (xhr.status === 200) {
                console.log("✅ XMLHttpRequest completed successfully");
                console.log("   - Status:", xhr.status);
                console.log("   - Response length:", xhr.responseText.length);
                
                try {
                    const data = JSON.parse(xhr.responseText);
                    console.log("   - Parsed title:", data.title);
                } catch (e) {
                    console.log("   - Response text:", xhr.responseText.substring(0, 100) + "...");
                }
            } else {
                console.log("❌ XMLHttpRequest failed with status:", xhr.status);
            }
        }
    };
    
    xhr.open('GET', 'https://jsonplaceholder.typicode.com/posts/2');
    xhr.setRequestHeader('Accept', 'application/json');
    xhr.send();
    
} catch (e) {
    console.log("❌ XMLHttpRequest test failed:", e.message);
}

// Test 5: Local server test (if available)
console.log("5. Testing local server request...");
try {
    fetch('http://192.168.1.100:8000/test_remote_script.js')
        .then(response => {
            console.log("✅ Local server response received");
            console.log("   - Status:", response.status);
            console.log("   - Content-Type:", response.headers.get('content-type'));
            return response.text();
        })
        .then(text => {
            console.log("✅ Local script fetched");
            console.log("   - Script length:", text.length);
            console.log("   - First 50 chars:", text.substring(0, 50) + "...");
        })
        .catch(error => {
            console.log("⚠️ Local server not available:", error.message);
        });
} catch (e) {
    console.log("⚠️ Local server test skipped:", e.message);
}

// Test 6: POST request test
console.log("6. Testing POST request with fetch...");
try {
    fetch('https://jsonplaceholder.typicode.com/posts', {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json',
        },
        body: JSON.stringify({
            title: 'Test from QuickJS',
            body: 'This is a test POST request from QuickJS with fetch polyfill',
            userId: 1
        })
    })
    .then(response => {
        console.log("✅ POST request completed");
        console.log("   - Status:", response.status);
        return response.json();
    })
    .then(data => {
        console.log("✅ POST response received");
        console.log("   - Created ID:", data.id);
        console.log("   - Title:", data.title);
    })
    .catch(error => {
        console.log("❌ POST request failed:", error.message);
    });
} catch (e) {
    console.log("❌ POST test failed:", e.message);
}

// Return summary for QuickJS
const summary = {
    polyfillsLoaded: {
        fetch: typeof fetch === 'function',
        XMLHttpRequest: typeof XMLHttpRequest === 'function'
    },
    testStatus: "HTTP polyfill tests initiated",
    timestamp: new Date().toISOString(),
    note: "Check logs for detailed test results"
};

JSON.stringify(summary, null, 2);
