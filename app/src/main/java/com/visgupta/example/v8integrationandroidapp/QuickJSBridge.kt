package com.visgupta.example.v8integrationandroidapp

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

/**
 * Bridge class for QuickJS JavaScript engine integration
 * Provides methods to initialize QuickJS, execute JavaScript code, and manage resources
 * QuickJS is a lightweight, fast JavaScript engine with ES2023 support
 */
class QuickJSBridge(private val context: android.content.Context) {

    companion object {
        private const val TAG = "QuickJSBridge"

        // Load the native library
        init {
            try {
                System.loadLibrary("v8integration")
                Log.i(TAG, "Native library loaded successfully")
            } catch (e: UnsatisfiedLinkError) {
                Log.e(TAG, "Failed to load native library", e)
            }
        }
    }

    // Network service for remote JavaScript loading
    private val networkService = NetworkService()
    private val httpService = HttpService()
    private val cacheService = CacheService(context)
    
    // Execution history for remote scripts
    private val executionHistory = mutableListOf<RemoteExecutionResult>()
    
    /**
     * Data class representing the result of remote JavaScript execution
     */
    data class RemoteExecutionResult(
        val url: String,
        val fileName: String,
        val timestamp: Long,
        val success: Boolean,
        val result: String,
        val executionTimeMs: Long,
        val contentLength: Int
    )

    // Native method declarations
    private external fun initializeQuickJS(): Boolean
    private external fun executeScript(script: String): String
    private external fun cleanupQuickJS()
    private external fun isInitialized(): Boolean
    private external fun resetContext(): Boolean
    
    // Bytecode compilation and execution methods
    private external fun compileScript(script: String): ByteArray?
    private external fun executeBytecode(bytecode: ByteArray): String
    
    // HTTP polyfill native methods
    private external fun nativeHttpRequest(url: String, optionsJson: String): String

    // ByteTransfer integration methods (native)
    private external fun nativeTestByteTransfer(data: ByteArray, bufferName: String?): Boolean
    private external fun nativeReadBytesFromTransfer(
        length: Int,
        offset: Int,
        bufferName: String?
    ): ByteArray?

    private external fun nativeRunQuickJSTests(): String

    private var initialized = false

    /**
     * Initialize the QuickJS JavaScript engine
     * @return true if initialization was successful, false otherwise
     */
    fun initialize(): Boolean {
        Log.i(TAG, "Initializing QuickJS Bridge")

        if (initialized) {
            Log.w(TAG, "QuickJS Bridge already initialized")
            return true
        }

        try {
            initialized = initializeQuickJS()
            Log.i(TAG, "QuickJS Bridge initialization: ${if (initialized) "SUCCESS" else "FAILED"}")

            if (!initialized) {
                Log.e(TAG, "QuickJS initialization failed - native function returned false")
            }

            return initialized
        } catch (e: UnsatisfiedLinkError) {
            Log.e(TAG, "QuickJS initialization failed - native library not loaded", e)
            initialized = false
            return false
        } catch (e: Exception) {
            Log.e(TAG, "QuickJS initialization failed - unexpected error", e)
            initialized = false
            return false
        }
    }

    /**
     * Execute JavaScript code in QuickJS engine
     * @param jsCode The JavaScript code to execute
     * @param isolatedExecution Whether to execute in an isolated context to avoid variable conflicts
     * @return The result of the JavaScript execution as a string
     */
    fun runJavaScript(jsCode: String, isolatedExecution: Boolean = false): String {
        if (!initialized) {
            val error = "❌ QuickJS Bridge not initialized. Call initialize() first."
            Log.e(TAG, error)
            return error
        }

        if (jsCode.isBlank()) {
            val error = "❌ JavaScript code cannot be empty"
            Log.e(TAG, error)
            return error
        }

        if (jsCode.length > 10000) {
            val error = "❌ JavaScript code too long (max 10,000 characters)"
            Log.e(TAG, error)
            return error
        }

        Log.i(TAG, "Executing JavaScript in QuickJS: $jsCode")

        try {
            // Wrap in IIFE if isolated execution is requested
            val finalCode = if (isolatedExecution) {
                // For isolated execution, wrap in IIFE
                "(function() { \n$jsCode\n })();"
            } else {
                jsCode
            }
            
            val result = executeScript(finalCode)
            Log.i(TAG, "QuickJS result: $result")

            if (result.startsWith("Error:") || result.startsWith("JavaScript Error:")) {
                Log.w(TAG, "JavaScript execution returned error: $result")
            }

            return result
        } catch (e: UnsatisfiedLinkError) {
            val error = "❌ Native library error during JavaScript execution"
            Log.e(TAG, error, e)
            return error
        } catch (e: Exception) {
            val error = "❌ Unexpected error during JavaScript execution: ${e.message}"
            Log.e(TAG, error, e)
            return error
        }
    }

    /**
     * Test various JavaScript operations specific to QuickJS features
     * @return Map of test results showcasing QuickJS capabilities
     */
    fun runTestSuite(): Map<String, String> {
        val results = mutableMapOf<String, String>()

        // Test 1: Simple arithmetic (same as V8 for comparison)
        results["arithmetic"] = runJavaScript("2 + 3 * 4")

        // Test 2: String manipulation
        results["string"] = runJavaScript("'Hello ' + 'World'")

        // Test 3: JSON creation
        results["json"] = runJavaScript("JSON.stringify({ name: 'QuickJS', value: 42 })")

        // Test 4: Function definition and call
        results["function"] = runJavaScript("function add(a, b) { return a + b; } add(15, 25)")

        // Test 5: Array operations
        results["array"] = runJavaScript("[1, 2, 3].map(x => x * 2).join(', ')")

        // Test 6: ES2023 features (QuickJS specific)
        results["es2023_destructuring"] = runJavaScript("const [a, b] = [10, 20]; a + b")

        // Test 7: Template literals
        results["template_literals"] = runJavaScript("const name = 'QuickJS'; `Hello \${name}!`")

        // Test 8: Arrow functions
        results["arrow_functions"] = runJavaScript("const square = x => x * x; square(7)")

        return results
    }

    /**
     * Test byte transfer from QuickJS to ByteTransfer system
     */
    fun testByteTransfer(data: ByteArray, bufferName: String? = null): Boolean {
        Log.i(
            TAG,
            "Testing QuickJS byte transfer with ${data.size} bytes to buffer '${bufferName ?: "shared"}'"
        )
        val result = nativeTestByteTransfer(data, bufferName)
        Log.i(TAG, "QuickJS byte transfer test: ${if (result) "SUCCESS" else "FAILED"}")
        return result
    }

    /**
     * Read bytes from ByteTransfer system via QuickJS
     */
    fun readBytesFromTransfer(
        length: Int,
        offset: Int = 0,
        bufferName: String? = null
    ): ByteArray? {
        Log.i(
            TAG,
            "Reading $length bytes from ByteTransfer buffer '${bufferName ?: "shared"}' at offset $offset via QuickJS"
        )
        val result = nativeReadBytesFromTransfer(length, offset, bufferName)
        Log.i(
            TAG,
            "QuickJS read from ByteTransfer: ${if (result != null) "SUCCESS (${result.size} bytes)" else "FAILED"}"
        )
        return result
    }

    /**
     * Run comprehensive QuickJS-specific tests
     */
    fun runQuickJSSpecificTests(): String {
        Log.i(TAG, "Running QuickJS-specific integration tests")
        val result = nativeRunQuickJSTests()
        Log.i(TAG, "QuickJS specific tests result: $result")
        return result
    }

    /**
     * Reset QuickJS context to clear variables and avoid conflicts
     */
    fun resetQuickJSContext(): Boolean {
        if (!initialized) {
            Log.w(TAG, "Cannot reset context: QuickJS not initialized")
            return false
        }
        
        Log.i(TAG, "Resetting QuickJS context to clear variables")
        return resetContext() // Call native method
    }
    
    /**
     * Compile JavaScript to bytecode (for testing and manual compilation)
     */
    fun compileJavaScriptToBytecode(script: String): ByteArray? {
        return try {
            if (!initialized) {
                Log.e(TAG, "QuickJS not initialized for bytecode compilation")
                return null
            }
            val bytecode = compileScript(script)
            if (bytecode != null) {
                Log.i(TAG, "Successfully compiled ${script.length} chars to ${bytecode.size} bytes of bytecode")
            }
            bytecode
        } catch (e: Exception) {
            Log.e(TAG, "Failed to compile JavaScript to bytecode", e)
            null
        }
    }
    
    /**
     * Execute bytecode directly (for testing)
     */
    fun executeBytecodeDirectly(bytecode: ByteArray): String {
        return try {
            if (!initialized) {
                return "Error: QuickJS not initialized"
            }
            val result = executeBytecode(bytecode)
            Log.i(TAG, "Executed ${bytecode.size} bytes of bytecode")
            result
        } catch (e: Exception) {
            Log.e(TAG, "Failed to execute bytecode", e)
            "Error: ${e.message}"
        }
    }
    
    /**
     * Run bytecode compilation test with demo JavaScript
     */
    fun runBytecodeTest(callback: RemoteExecutionCallback) {
        Log.i(TAG, "Starting bytecode compilation test")
        callback.onProgress("🔨 Starting Bytecode Compilation Test...")
        
        CoroutineScope(Dispatchers.Main).launch {
            try {
                val startTime = System.currentTimeMillis()
                
                // Demo JavaScript for testing bytecode compilation
                val testScript = """
                    // Bytecode Compilation Test
                    console.log("🔧 Testing JavaScript Bytecode Compilation");
                    
                    // Test 1: Basic computation
                    function fibonacci(n) {
                        if (n <= 1) return n;
                        return fibonacci(n - 1) + fibonacci(n - 2);
                    }
                    
                    const startTime = Date.now();
                    const fibResult = fibonacci(15);
                    const computeTime = Date.now() - startTime;
                    
                    // Test 2: Object creation and manipulation
                    const testResult = {
                        testName: "Bytecode Compilation Test",
                        timestamp: new Date().toISOString(),
                        fibonacci15: fibResult,
                        computeTimeMs: computeTime,
                        features: [
                            "JavaScript to bytecode compilation",
                            "Bytecode caching and storage",
                            "Direct bytecode execution",
                            "Performance comparison"
                        ],
                        performance: {
                            note: "Bytecode execution is much faster than parsing source code",
                            benefit: "50-90% faster execution on subsequent runs"
                        }
                    };
                    
                    console.log("✅ Bytecode compilation test completed");
                    
                    // Return JSON result
                    return JSON.stringify(testResult, null, 2);
                """.trimIndent()
                
                callback.onProgress("📝 Compiling test script to bytecode...")
                
                // Step 1: Compile to bytecode
                val compilationStart = System.currentTimeMillis()
                val bytecode = compileJavaScriptToBytecode(testScript)
                val compilationTime = System.currentTimeMillis() - compilationStart
                
                if (bytecode == null) {
                    callback.onError("BYTECODE_TEST", "Failed to compile JavaScript to bytecode")
                    return@launch
                }
                
                callback.onProgress("⚡ Executing compiled bytecode...")
                
                // Step 2: Execute bytecode
                val executionStart = System.currentTimeMillis()
                val result = executeBytecodeDirectly(bytecode)
                val executionTime = System.currentTimeMillis() - executionStart
                
                val totalTime = System.currentTimeMillis() - startTime
                
                // Create execution result
                val executionResult = RemoteExecutionResult(
                    url = "BYTECODE_TEST",
                    fileName = "bytecode_test.js",
                    timestamp = System.currentTimeMillis(),
                    success = !result.startsWith("Error:") && !result.startsWith("JavaScript Error:") && !result.startsWith("Bytecode Error:"),
                    result = result,
                    executionTimeMs = executionTime,
                    contentLength = bytecode.size
                )
                
                // Add to history
                executionHistory.add(0, executionResult)
                if (executionHistory.size > 50) {
                    executionHistory.removeAt(executionHistory.size - 1)
                }
                
                Log.i(TAG, "✅ Bytecode test completed - Compilation: ${compilationTime}ms, Execution: ${executionTime}ms, Bytecode: ${bytecode.size} bytes")
                callback.onSuccess(executionResult)
                
            } catch (e: Exception) {
                Log.e(TAG, "Bytecode test failed", e)
                callback.onError("BYTECODE_TEST", "Bytecode test failed: ${e.message}")
            }
        }
    }

    /**
     * Handle HTTP request from JavaScript (called from native code)
     * This method is called from the native HTTP polyfill implementation
     */
    fun handleHttpRequest(url: String, optionsJson: String): String {
        return try {
            Log.d(TAG, "Handling HTTP request from JS: $url")
            
            val options = httpService.parseRequestOptions(optionsJson)
            
            // Execute the request with caching support and timeout
            val response = runBlocking {
                withTimeout(30000) { // 30 second timeout
                    httpService.executeRequest(url, options, cacheService)
                }
            }
            
            val responseJson = httpService.responseToJson(response)
            Log.d(TAG, "HTTP request completed: ${response.status}")
            
            responseJson
        } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
            Log.e(TAG, "HTTP request timed out: $url", e)
            val errorResponse = HttpService.HttpResponse(
                status = 408,
                statusText = "Request Timeout",
                headers = emptyMap(),
                body = "",
                url = url
            )
            httpService.responseToJson(errorResponse)
        } catch (e: Exception) {
            Log.e(TAG, "Error handling HTTP request", e)
            val errorResponse = HttpService.HttpResponse(
                status = 0,
                statusText = "Internal Error: ${e.message}",
                headers = emptyMap(),
                body = "",
                url = url
            )
            httpService.responseToJson(errorResponse)
        }
    }

    /**
     * Get cache statistics
     */
    fun getCacheStats(): CacheService.CacheStats {
        return cacheService.getCacheStats()
    }
    
    /**
     * Get list of cached URLs
     */
    fun getCachedUrls(): List<String> {
        return cacheService.getCachedUrls()
    }
    
    /**
     * Clear all cache
     */
    suspend fun clearCache() {
        cacheService.clearCache()
        Log.i(TAG, "Cache cleared")
    }
    
    /**
     * Remove specific cache entry
     */
    suspend fun removeCacheEntry(url: String): Boolean {
        return cacheService.removeEntry(url)
    }

    /**
     * Cleanup QuickJS resources
     */
    fun cleanup() {
        Log.i(TAG, "Cleaning up QuickJS Bridge")
        httpService.cleanup()
        cleanupQuickJS()
        initialized = false
        Log.i(TAG, "QuickJS Bridge cleanup complete")
    }

    /**
     * Check if QuickJS is initialized
     */
    fun isQuickJSInitialized(): Boolean = initialized && isInitialized()

    /**
     * Get engine information
     */
    fun getEngineInfo(): String {
        return if (initialized) {
            "QuickJS Engine v2025.04.26\n" +
                    "✅ ES2023 specification support\n" +
                    "✅ Lightweight (367 KiB footprint)\n" +
                    "✅ Fast startup time (<300μs)\n" +
                    "✅ Module system support\n" +
                    "✅ Async/await support\n" +
                    "✅ BigInt arithmetic support\n" +
                    "✅ Atomics and SharedArrayBuffer\n" +
                    "✅ Memory-limited (64MB max)\n" +
                    "✅ Optimized garbage collection\n" +
                    "✅ ByteTransfer integration"
        } else {
            "❌ QuickJS Engine not initialized"
        }
    }

    /**
     * Get memory usage statistics
     */
    fun getMemoryStats(): String {
        return if (initialized) {
            nativeGetMemoryStats()
        } else {
            "❌ QuickJS not initialized"
        }
    }

    private external fun nativeGetMemoryStats(): String
    
    /**
     * Callback interface for remote JavaScript execution
     */
    interface RemoteExecutionCallback {
        fun onProgress(message: String)
        fun onSuccess(result: RemoteExecutionResult)
        fun onError(url: String, error: String)
    }
    
    /**
     * Execute JavaScript code from a remote URL
     * @param url The URL to fetch JavaScript from
     * @param callback Callback for handling results
     */
    fun executeRemoteJavaScript(url: String, callback: RemoteExecutionCallback) {
        if (!initialized) {
            callback.onError(url, "❌ QuickJS Bridge not initialized. Call initialize() first.")
            return
        }
        
        if (url.isBlank()) {
            callback.onError(url, "❌ URL cannot be empty")
            return
        }
        
        Log.i(TAG, "Starting remote JavaScript execution from: $url")
        callback.onProgress("🌐 Fetching JavaScript from server...")
        
        // Use coroutine for network operation with caching
        CoroutineScope(Dispatchers.Main).launch {
            try {
                callback.onProgress("🔍 Checking cache for: $url")
                val startTime = System.currentTimeMillis()
                
                // Check if we have cached bytecode
                val cachedBytecode = withContext(Dispatchers.IO) {
                    cacheService.getCachedBytecode(url)
                }
                
                if (cachedBytecode != null) {
                    callback.onProgress("⚡ Executing cached bytecode...")
                    val executionStartTime = System.currentTimeMillis()
                    
                    // Reset context to prevent variable redeclaration issues
                    if (!resetQuickJSContext()) {
                        Log.w(TAG, "Failed to reset context for bytecode execution, continuing...")
                    }
                    
                    // Execute the compiled bytecode directly (much faster than parsing source)
                    val result = executeBytecode(cachedBytecode)
                    val executionTime = System.currentTimeMillis() - executionStartTime
                    
                    val executionResult = RemoteExecutionResult(
                        url = url,
                        fileName = networkService.getFileNameFromUrl(url),
                        timestamp = System.currentTimeMillis(),
                        success = !result.startsWith("Error:") && !result.startsWith("JavaScript Error:") && !result.startsWith("Bytecode Error:"),
                        result = result,
                        executionTimeMs = executionTime,
                        contentLength = cachedBytecode.size
                    )
                    
                    executionHistory.add(0, executionResult)
                    if (executionHistory.size > 50) {
                        executionHistory.removeAt(executionHistory.size - 1)
                    }
                    
                    Log.i(TAG, "✅ Executed cached bytecode for: $url (${cachedBytecode.size} bytes, ${executionTime}ms)")
                    callback.onSuccess(executionResult)
                    return@launch
                }
                
                // Check for cached source code
                val cachedEntry = withContext(Dispatchers.IO) {
                    cacheService.getCachedEntry(url)
                }
                
                if (cachedEntry != null) {
                    Log.d(TAG, "Found cached entry - etag: ${cachedEntry.etag}, lastModified: ${cachedEntry.lastModified}, needsRevalidation: ${cachedEntry.needsRevalidation()}")
                    if (!cachedEntry.needsRevalidation()) {
                        callback.onProgress("⚡ Executing cached JavaScript...")
                        val executionStartTime = System.currentTimeMillis()
                        
                        // Reset context to prevent variable redeclaration issues
                        if (!resetQuickJSContext()) {
                            Log.w(TAG, "Failed to reset context, continuing with isolated execution")
                        }
                        
                        val sanitizedCode = networkService.sanitizeJavaScript(cachedEntry.sourceCode)
                        
                        // Try to compile and cache bytecode if not already cached (async, don't block execution)
                        if (cachedEntry.bytecode == null) {
                            CoroutineScope(Dispatchers.IO).launch {
                                try {
                                    val bytecode = compileScript(sanitizedCode)
                                    if (bytecode != null) {
                                        val cached = cacheService.cacheBytecode(url, bytecode)
                                        if (cached) {
                                            Log.i(TAG, "✅ Compiled and cached bytecode for cached source: $url (${bytecode.size} bytes)")
                                        }
                                    }
                                } catch (e: Exception) {
                                    Log.w(TAG, "Background bytecode compilation failed for cached source: $url", e)
                                }
                            }
                        }
                        
                        // Use isolated execution for cached scripts to prevent variable conflicts
                        val result = runJavaScript(sanitizedCode, isolatedExecution = true)
                        val executionTime = System.currentTimeMillis() - executionStartTime
                        
                        val executionResult = RemoteExecutionResult(
                            url = url,
                            fileName = networkService.getFileNameFromUrl(url),
                            timestamp = System.currentTimeMillis(),
                            success = !result.startsWith("Error:") && !result.startsWith("JavaScript Error:"),
                            result = result,
                            executionTimeMs = executionTime,
                            contentLength = cachedEntry.sourceCode.length
                        )
                        
                        executionHistory.add(0, executionResult)
                        if (executionHistory.size > 50) {
                            executionHistory.removeAt(executionHistory.size - 1)
                        }
                        
                        Log.i(TAG, "✅ Executed cached JavaScript for: $url")
                        callback.onSuccess(executionResult)
                        return@launch
                    }
                }
                
                // Fetch from network with caching
                callback.onProgress("📡 Fetching JavaScript from server...")
                
                // Fetch JavaScript from remote server
                val networkResult = withContext(Dispatchers.IO) {
                    networkService.fetchJavaScript(url)
                }
                
                when (networkResult) {
                    is NetworkService.NetworkResult.Success -> {
                        callback.onProgress("✅ JavaScript fetched, caching and executing...")
                        
                        // Cache the downloaded content
                        withContext(Dispatchers.IO) {
                            cacheService.cacheEntry(
                                url = url,
                                sourceCode = networkResult.content,
                                contentType = networkResult.contentType
                            )
                        }
                        
                        // Validate content
                        if (!networkService.isJavaScriptContent(networkResult.contentType, url)) {
                            Log.w(TAG, "Content may not be JavaScript: ${networkResult.contentType}")
                            callback.onProgress("⚠️ Content may not be JavaScript, executing anyway...")
                        }
                        
                        // Sanitize and execute
                        val sanitizedCode = networkService.sanitizeJavaScript(networkResult.content)
                        val executionStart = System.currentTimeMillis()
                        
                        // Compile to bytecode for future caching (async, don't block execution)
                        CoroutineScope(Dispatchers.IO).launch {
                            try {
                                callback.onProgress("🔨 Compiling JavaScript to bytecode...")
                                val bytecode = compileScript(sanitizedCode)
                                if (bytecode != null) {
                                    val cached = cacheService.cacheBytecode(url, bytecode)
                                    if (cached) {
                                        Log.i(TAG, "✅ Compiled and cached bytecode for: $url (${bytecode.size} bytes)")
                                    } else {
                                        Log.w(TAG, "Failed to cache compiled bytecode for: $url")
                                    }
                                } else {
                                    Log.w(TAG, "Failed to compile JavaScript to bytecode for: $url")
                                }
                            } catch (e: Exception) {
                                Log.w(TAG, "Bytecode compilation failed for: $url", e)
                            }
                        }
                        
                        // Use isolated execution for downloaded scripts to prevent variable conflicts
                        val result = runJavaScript(sanitizedCode, isolatedExecution = true)
                        val executionTime = System.currentTimeMillis() - executionStart
                        
                        // Create result object
                        val fileName = networkService.getFileNameFromUrl(url)
                        val executionResult = RemoteExecutionResult(
                            url = url,
                            fileName = fileName,
                            timestamp = System.currentTimeMillis(),
                            success = !result.startsWith("Error:"),
                            result = result,
                            executionTimeMs = executionTime,
                            contentLength = networkResult.content.length
                        )
                        
                        // Store in history
                        executionHistory.add(0, executionResult) // Add to beginning
                        if (executionHistory.size > 50) { // Keep last 50 executions
                            executionHistory.removeAt(executionHistory.size - 1)
                        }
                        
                        val totalTime = System.currentTimeMillis() - startTime
                        Log.i(TAG, "Remote JavaScript execution completed in ${totalTime}ms (network: ${totalTime - executionTime}ms, execution: ${executionTime}ms)")
                        
                        callback.onSuccess(executionResult)
                    }
                    
                    is NetworkService.NetworkResult.Error -> {
                        val errorMsg = "Network error: ${networkResult.message}"
                        Log.e(TAG, errorMsg)
                        callback.onError(url, errorMsg)
                    }
                }
                
            } catch (e: Exception) {
                val errorMsg = "Unexpected error during remote execution: ${e.message}"
                Log.e(TAG, errorMsg, e)
                callback.onError(url, errorMsg)
            }
        }
    }
    
    /**
     * Get execution history
     */
    fun getExecutionHistory(): List<RemoteExecutionResult> {
        return executionHistory.toList()
    }
    
    /**
     * Clear execution history
     */
    fun clearExecutionHistory() {
        executionHistory.clear()
        Log.i(TAG, "Execution history cleared")
    }
    
    /**
     * Get popular JavaScript CDN URLs for testing
     */
    fun getPopularJavaScriptUrls(): List<Pair<String, String>> {
        return listOf(
            "Test Remote Script" to "LOCAL_SERVER/test_remote_script.js", // Placeholder for local server
            "Test HTTP Polyfills" to "LOCAL_SERVER/test_fetch_polyfill.js", // Test fetch() and XMLHttpRequest
            "Test Cache System (Fast)" to "LOCAL_SERVER/test_cache_system_fast.js", // Fast cache test
            "Test Cache System" to "LOCAL_SERVER/test_cache_system.js", // Full cache test with HTTP
            "Test Bytecode Compilation" to "BYTECODE_TEST", // Test bytecode compilation and execution
            "Lodash Utility" to "https://cdn.jsdelivr.net/npm/lodash@4.17.21/lodash.min.js",
            "Moment.js Date" to "https://cdn.jsdelivr.net/npm/moment@2.29.4/moment.min.js",
            "Math.js Library" to "https://cdn.jsdelivr.net/npm/mathjs@11.11.0/lib/browser/math.min.js",
            "Simple Test Script" to "https://raw.githubusercontent.com/mdn/beginner-html-site-scripted/gh-pages/scripts/main.js",
            "D3.js Visualization" to "https://cdn.jsdelivr.net/npm/d3@7.8.5/dist/d3.min.js"
        )
    }
    
    /**
     * Build URL for local server script
     */
    fun buildLocalServerUrl(ip: String, port: String, script: String = "test_remote_script.js"): String {
        val cleanIp = ip.trim()
        val cleanPort = port.trim()
        
        return if (cleanIp.isNotEmpty() && cleanPort.isNotEmpty()) {
            "http://$cleanIp:$cleanPort/$script"
        } else {
            "http://192.168.1.100:8000/$script" // Default example
        }
    }
    
    /**
     * Test remote execution with a simple script
     */
    fun testRemoteExecution(callback: RemoteExecutionCallback) {
        // Create a simple test script with unique variable names to avoid conflicts
        val testScript = """
            // QuickJS Internal Test Script
            (function() {
                const testMessage = "Hello from Internal Test Script!";
                const testTimestamp = new Date().toISOString();
                const testResult = {
                    message: testMessage,
                    timestamp: testTimestamp,
                    engine: "QuickJS",
                    version: "2025-04-26",
                    features: ["ES2023", "Modules", "Async/Await", "BigInt"],
                    test: "Internal test execution successful!",
                    type: "internal_test",
                    executionId: Math.random().toString(36).substr(2, 9)
                };
                return JSON.stringify(testResult, null, 2);
            })();
        """.trimIndent()
        
        // For testing, we'll execute the script directly
        // In a real scenario, you'd host this on a web server
        callback.onProgress("🧪 Running test script...")
        
        try {
            // The test script is already wrapped in IIFE, so don't use isolatedExecution
            val result = runJavaScript(testScript, isolatedExecution = false)
            val executionResult = RemoteExecutionResult(
                url = "test://internal",
                fileName = "internal_test.js",
                timestamp = System.currentTimeMillis(),
                success = !result.startsWith("Error:") && !result.startsWith("JavaScript Error:") && result != "undefined",
                result = result,
                executionTimeMs = 0,
                contentLength = testScript.length
            )
            callback.onSuccess(executionResult)
        } catch (e: Exception) {
            callback.onError("test://internal", "Test execution failed: ${e.message}")
        }
    }
}