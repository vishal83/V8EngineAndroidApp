package com.visgupta.example.v8integrationandroidapp

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

/**
 * Bridge class for QuickJS JavaScript engine integration
 * Provides methods to initialize QuickJS, execute JavaScript code, and manage resources
 * QuickJS is a lightweight, fast JavaScript engine with ES2023 support
 */
class QuickJSBridge {

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
    
    // Execution history for remote scripts
    private val executionHistory = mutableListOf<RemoteExecutionResult>()

    // Native method declarations
    private external fun initializeQuickJS(): Boolean
    private external fun executeScript(script: String): String
    private external fun cleanupQuickJS()
    private external fun isInitialized(): Boolean
    
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
                // For isolated execution, wrap in IIFE and ensure proper return
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
    fun resetContext(): Boolean {
        if (!initialized) {
            Log.w(TAG, "Cannot reset context: QuickJS not initialized")
            return false
        }
        
        Log.i(TAG, "Resetting QuickJS context to clear variables")
        
        try {
            // Cleanup and reinitialize to get a fresh context
            cleanupQuickJS()
            val success = initializeQuickJS()
            
            if (success) {
                Log.i(TAG, "QuickJS context reset successfully")
            } else {
                Log.e(TAG, "Failed to reinitialize QuickJS after context reset")
                initialized = false
            }
            
            return success
        } catch (e: Exception) {
            Log.e(TAG, "Error during context reset", e)
            initialized = false
            return false
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
            
            // Execute the request synchronously for now (JavaScript will handle async)
            val response = runBlocking {
                httpService.executeRequest(url, options)
            }
            
            val responseJson = httpService.responseToJson(response)
            Log.d(TAG, "HTTP request completed: ${response.status}")
            
            responseJson
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
     * Data class for remote execution results
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
        
        // Use coroutine for network operation
        CoroutineScope(Dispatchers.Main).launch {
            try {
                val startTime = System.currentTimeMillis()
                
                // Fetch JavaScript from remote server
                val networkResult = withContext(Dispatchers.IO) {
                    networkService.fetchJavaScript(url)
                }
                
                when (networkResult) {
                    is NetworkService.NetworkResult.Success -> {
                        callback.onProgress("✅ JavaScript fetched, executing...")
                        
                        // Validate content
                        if (!networkService.isJavaScriptContent(networkResult.contentType, url)) {
                            Log.w(TAG, "Content may not be JavaScript: ${networkResult.contentType}")
                            callback.onProgress("⚠️ Content may not be JavaScript, executing anyway...")
                        }
                        
                        // Sanitize and execute
                        val sanitizedCode = networkService.sanitizeJavaScript(networkResult.content)
                        val executionStart = System.currentTimeMillis()
                        val result = executeScript(sanitizedCode)
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