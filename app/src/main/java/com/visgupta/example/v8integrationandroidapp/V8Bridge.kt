package com.visgupta.example.v8integrationandroidapp

import android.util.Log

/**
 * Bridge class for V8 JavaScript engine integration
 * Provides methods to initialize V8, execute JavaScript code, and manage resources
 */
class V8Bridge {
    
    companion object {
        private const val TAG = "V8Bridge"
        
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
    private external fun initializeV8(): Boolean
    private external fun executeScript(script: String): String
    private external fun cleanupV8()
    private external fun testDataExchange(input: String, number: Int, flag: Boolean): String
    
    private var isInitialized = false
    
    /**
     * Initialize the V8 JavaScript engine
     * @return true if initialization was successful, false otherwise
     */
    fun initialize(): Boolean {
        Log.i(TAG, "Initializing V8 Bridge")
        
        if (isInitialized) {
            Log.w(TAG, "V8 Bridge already initialized")
            return true
        }
        
        try {
            isInitialized = initializeV8()
            Log.i(TAG, "V8 Bridge initialization: ${if (isInitialized) "SUCCESS" else "FAILED"}")
            
            if (!isInitialized) {
                Log.e(TAG, "V8 initialization failed - native function returned false")
            }
            
            return isInitialized
        } catch (e: UnsatisfiedLinkError) {
            Log.e(TAG, "V8 initialization failed - native library not loaded", e)
            isInitialized = false
            return false
        } catch (e: Exception) {
            Log.e(TAG, "V8 initialization failed - unexpected error", e)
            isInitialized = false
            return false
        }
    }
    
    /**
     * Execute JavaScript code in V8 engine
     * @param jsCode The JavaScript code to execute
     * @return The result of the JavaScript execution as a string
     */
    fun runJavaScript(jsCode: String): String {
        if (!isInitialized) {
            val error = "❌ V8 Bridge not initialized. Call initialize() first."
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
        
        Log.i(TAG, "Executing JavaScript: $jsCode")
        
        try {
            val result = executeScript(jsCode)
            Log.i(TAG, "JavaScript result: $result")
            
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
     * Test data exchange between Kotlin and C++
     * @param stringData Test string data
     * @param numberData Test integer data
     * @param booleanData Test boolean data
     * @return Formatted string with all the data
     */
    fun testDataFlow(stringData: String, numberData: Int, booleanData: Boolean): String {
        Log.i(TAG, "Testing data exchange - String: $stringData, Number: $numberData, Boolean: $booleanData")
        val result = testDataExchange(stringData, numberData, booleanData)
        Log.i(TAG, "Data exchange result: $result")
        return result
    }
    
    /**
     * Test various JavaScript operations
     * @return Map of test results
     */
    fun runTestSuite(): Map<String, String> {
        val results = mutableMapOf<String, String>()
        
        // Test 1: Simple arithmetic
        results["arithmetic"] = runJavaScript("2 + 3 * 4")
        
        // Test 2: String manipulation
        results["string"] = runJavaScript("'Hello ' + 'World'")
        
        // Test 3: JSON creation
        results["json"] = runJavaScript("JSON.stringify({ name: 'Test', value: 42 })")
        
        // Test 4: Function definition and call
        results["function"] = runJavaScript("function add(a, b) { return a + b; } add(10, 20)")
        
        // Test 5: Array operations
        results["array"] = runJavaScript("[1, 2, 3].map(x => x * 2).join(', ')")
        
        return results
    }
    
    // ByteTransfer integration methods (native)
    private external fun nativeTestByteTransfer(data: ByteArray, bufferName: String?): Boolean
    private external fun nativeReadBytesFromTransfer(length: Int, offset: Int, bufferName: String?): ByteArray?
    private external fun nativeGetByteTransferInfo(bufferName: String?): String
    private external fun nativeTransferBytesToBuffer(data: ByteArray, bufferName: String?): Boolean
    private external fun nativeReadBytesFromBuffer(length: Int, offset: Int, bufferName: String?): ByteArray?
    private external fun nativeGetBufferInfoFromV8(bufferName: String?): String
    private external fun nativeRunV8ByteTransferTests(): String
    
    /**
     * Test byte transfer from V8 to ByteTransfer system
     */
    fun testByteTransfer(data: ByteArray, bufferName: String? = null): Boolean {
        Log.i(TAG, "Testing byte transfer with ${data.size} bytes to buffer '${bufferName ?: "shared"}'")
        val result = nativeTestByteTransfer(data, bufferName)
        Log.i(TAG, "Byte transfer test: ${if (result) "SUCCESS" else "FAILED"}")
        return result
    }
    
    /**
     * Read bytes from ByteTransfer system via V8
     */
    fun readBytesFromTransfer(length: Int, offset: Int = 0, bufferName: String? = null): ByteArray? {
        Log.i(TAG, "Reading $length bytes from ByteTransfer buffer '${bufferName ?: "shared"}' at offset $offset")
        val result = nativeReadBytesFromTransfer(length, offset, bufferName)
        Log.i(TAG, "Read from ByteTransfer: ${if (result != null) "SUCCESS (${result.size} bytes)" else "FAILED"}")
        return result
    }
    
    /**
     * Get ByteTransfer buffer information via V8
     */
    fun getByteTransferInfo(bufferName: String? = null): String {
        Log.i(TAG, "Getting ByteTransfer info for buffer '${bufferName ?: "shared"}'")
        val result = nativeGetByteTransferInfo(bufferName)
        Log.i(TAG, "ByteTransfer info: $result")
        return result
    }
    
    /**
     * Transfer bytes to buffer (public wrapper for Kotlin)
     */
    fun transferBytesToBuffer(data: ByteArray, bufferName: String? = null): Boolean {
        Log.i(TAG, "Transferring ${data.size} bytes to buffer '${bufferName ?: "shared"}'")
        val result = nativeTransferBytesToBuffer(data, bufferName)
        Log.i(TAG, "Transfer to buffer: ${if (result) "SUCCESS" else "FAILED"}")
        return result
    }
    
    /**
     * Read bytes from buffer (public wrapper for Kotlin)
     */
    fun readBytesFromBuffer(length: Int, offset: Int = 0, bufferName: String? = null): ByteArray? {
        Log.i(TAG, "Reading $length bytes from buffer '${bufferName ?: "shared"}' at offset $offset")
        val result = nativeReadBytesFromBuffer(length, offset, bufferName)
        Log.i(TAG, "Read from buffer: ${if (result != null) "SUCCESS (${result.size} bytes)" else "FAILED"}")
        return result
    }
    
    /**
     * Get buffer information from V8 perspective
     */
    fun getBufferInfoFromV8(bufferName: String? = null): String {
        Log.i(TAG, "Getting buffer info from V8 for '${bufferName ?: "shared"}'")
        val result = nativeGetBufferInfoFromV8(bufferName)
        Log.i(TAG, "V8 buffer info: $result")
        return result
    }
    
    /**
     * Run comprehensive V8 ↔ ByteTransfer tests
     */
    fun runV8ByteTransferTests(): String {
        Log.i(TAG, "Running V8 ↔ ByteTransfer integration tests")
        val result = nativeRunV8ByteTransferTests()
        Log.i(TAG, "V8 ByteTransfer tests result: $result")
        return result
    }

    /**
     * Cleanup V8 resources
     */
    fun cleanup() {
        Log.i(TAG, "Cleaning up V8 Bridge")
        cleanupV8()
        isInitialized = false
        Log.i(TAG, "V8 Bridge cleanup complete")
    }
    
    /**
     * Check if V8 is initialized
     */
    fun isV8Initialized(): Boolean = isInitialized
}