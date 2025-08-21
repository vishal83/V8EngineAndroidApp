package com.visgupta.example.v8integrationandroidapp

import android.util.Log

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

    // Native method declarations
    private external fun initializeQuickJS(): Boolean
    private external fun executeScript(script: String): String
    private external fun cleanupQuickJS()
    private external fun isInitialized(): Boolean

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
     * @return The result of the JavaScript execution as a string
     */
    fun runJavaScript(jsCode: String): String {
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
            val result = executeScript(jsCode)
            Log.i(TAG, "QuickJS result: $result")

            if (result.startsWith("Error:")) {
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
     * Cleanup QuickJS resources
     */
    fun cleanup() {
        Log.i(TAG, "Cleaning up QuickJS Bridge")
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
}