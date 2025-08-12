package com.visgupta.example.v8integrationandroidapp

import android.util.Log

class V8Bridge {
    
    companion object {
        private const val TAG = "V8Bridge"
        
        init {
            try {
                System.loadLibrary("v8integration")
                Log.i(TAG, "Native library loaded successfully")
            } catch (e: UnsatisfiedLinkError) {
                Log.e(TAG, "Failed to load native library", e)
            }
        }
    }
    
    private external fun initializeV8(): Boolean
    private external fun executeScript(script: String): String
    private external fun cleanupV8()
    private external fun testDataExchange(input: String, number: Int, flag: Boolean): String
    
    private var isInitialized = false
    
    fun initialize(): Boolean {
        Log.i(TAG, "Initializing V8 Bridge")
        isInitialized = initializeV8()
        Log.i(TAG, "V8 Bridge initialization: ${if (isInitialized) "SUCCESS" else "FAILED"}")
        return isInitialized
    }
    
    fun runJavaScript(jsCode: String): String {
        if (!isInitialized) {
            val error = "V8 Bridge not initialized. Call initialize() first."
            Log.e(TAG, error)
            return error
        }
        
        Log.i(TAG, "Executing JavaScript: $jsCode")
        val result = executeScript(jsCode)
        Log.i(TAG, "JavaScript result: $result")
        return result
    }
    
    fun testDataFlow(stringData: String, numberData: Int, booleanData: Boolean): String {
        Log.i(TAG, "Testing data exchange")
        val result = testDataExchange(stringData, numberData, booleanData)
        Log.i(TAG, "Data exchange result: $result")
        return result
    }
    
    fun runTestSuite(): Map<String, String> {
        val results = mutableMapOf<String, String>()
        results["arithmetic"] = runJavaScript("2 + 3 * 4")
        results["string"] = runJavaScript("'Hello ' + 'World'")
        results["json"] = runJavaScript("JSON.stringify({ name: 'Test', value: 42 })")
        results["function"] = runJavaScript("function add(a, b) { return a + b; } add(10, 20)")
        return results
    }
    
    fun cleanup() {
        Log.i(TAG, "Cleaning up V8 Bridge")
        cleanupV8()
        isInitialized = false
        Log.i(TAG, "V8 Bridge cleanup complete")
    }
    
    fun isV8Initialized(): Boolean = isInitialized
}
