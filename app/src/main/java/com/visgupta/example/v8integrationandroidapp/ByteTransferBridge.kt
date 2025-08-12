package com.visgupta.example.v8integrationandroidapp

import android.util.Log

class ByteTransferBridge {
    
    companion object {
        private const val TAG = "ByteTransferBridge"
        
        init {
            try {
                System.loadLibrary("v8integration")
                Log.i(TAG, "Native library loaded successfully")
            } catch (e: UnsatisfiedLinkError) {
                Log.e(TAG, "Failed to load native library", e)
            }
        }
    }
    
    private external fun nativeInitializeByteTransfer(bufferSize: Int): Boolean
    private external fun nativeCreateNamedBuffer(name: String, size: Int): Boolean
    private external fun nativeWriteBytes(data: ByteArray): Boolean
    private external fun nativeWriteBytesToNamed(name: String, data: ByteArray): Boolean
    private external fun nativeReadBytes(length: Int, offset: Int): ByteArray?
    private external fun nativeReadBytesFromNamed(name: String, length: Int, offset: Int): ByteArray?
    private external fun nativeGetBufferInfo(name: String?): BufferInfo?
    private external fun nativeClearBuffer(name: String?)
    private external fun nativeCleanup()
    
    private var isInitialized = false
    
    fun initialize(bufferSize: Int = 1024 * 1024): Boolean {
        Log.i(TAG, "Initializing byte transfer system with buffer size: $bufferSize")
        
        if (isInitialized) {
            Log.w(TAG, "ByteTransfer system already initialized")
            return true
        }
        
        if (bufferSize <= 0) {
            Log.e(TAG, "Invalid buffer size: $bufferSize")
            return false
        }
        
        if (bufferSize > 100 * 1024 * 1024) { // 100MB limit
            Log.e(TAG, "Buffer size too large: $bufferSize (max 100MB)")
            return false
        }
        
        try {
            isInitialized = nativeInitializeByteTransfer(bufferSize)
            Log.i(TAG, "Byte transfer initialization: ${if (isInitialized) "SUCCESS" else "FAILED"}")
            
            if (!isInitialized) {
                Log.e(TAG, "ByteTransfer initialization failed - native function returned false")
            }
            
            return isInitialized
        } catch (e: UnsatisfiedLinkError) {
            Log.e(TAG, "ByteTransfer initialization failed - native library not loaded", e)
            isInitialized = false
            return false
        } catch (e: Exception) {
            Log.e(TAG, "ByteTransfer initialization failed - unexpected error", e)
            isInitialized = false
            return false
        }
    }
    
    fun createBuffer(name: String, size: Int): Boolean {
        Log.i(TAG, "Creating named buffer '$name' with size $size")
        val result = nativeCreateNamedBuffer(name, size)
        Log.i(TAG, "Buffer '$name' creation: ${if (result) "SUCCESS" else "FAILED"}")
        return result
    }
    
    fun writeToSharedBuffer(data: ByteArray): Boolean {
        if (!isInitialized) {
            Log.e(TAG, "Byte transfer system not initialized")
            return false
        }
        
        Log.i(TAG, "Writing ${data.size} bytes to shared buffer")
        val result = nativeWriteBytes(data)
        Log.i(TAG, "Write to shared buffer: ${if (result) "SUCCESS" else "FAILED"}")
        return result
    }
    
    fun writeToNamedBuffer(bufferName: String, data: ByteArray): Boolean {
        Log.i(TAG, "Writing ${data.size} bytes to buffer '$bufferName'")
        val result = nativeWriteBytesToNamed(bufferName, data)
        Log.i(TAG, "Write to buffer '$bufferName': ${if (result) "SUCCESS" else "FAILED"}")
        return result
    }
    
    fun readFromSharedBuffer(length: Int, offset: Int = 0): ByteArray? {
        if (!isInitialized) {
            Log.e(TAG, "Byte transfer system not initialized")
            return null
        }
        
        Log.i(TAG, "Reading $length bytes from shared buffer at offset $offset")
        val result = nativeReadBytes(length, offset)
        Log.i(TAG, "Read from shared buffer: ${if (result != null) "SUCCESS (${result.size} bytes)" else "FAILED"}")
        return result
    }
    
    fun readFromNamedBuffer(bufferName: String, length: Int, offset: Int = 0): ByteArray? {
        Log.i(TAG, "Reading $length bytes from buffer '$bufferName' at offset $offset")
        val result = nativeReadBytesFromNamed(bufferName, length, offset)
        Log.i(TAG, "Read from buffer '$bufferName': ${if (result != null) "SUCCESS (${result.size} bytes)" else "FAILED"}")
        return result
    }
    
    fun getBufferInfo(bufferName: String? = null): BufferInfo? {
        val info = nativeGetBufferInfo(bufferName)
        Log.i(TAG, "Buffer info for '${bufferName ?: "shared"}': $info")
        return info
    }
    
    fun clearBuffer(bufferName: String? = null) {
        Log.i(TAG, "Clearing buffer '${bufferName ?: "shared"}'")
        nativeClearBuffer(bufferName)
    }
    
    fun runByteTransferTests(): Map<String, String> {
        val results = mutableMapOf<String, String>()
        
        try {
            val testString = "Hello, Byte Transfer!"
            val stringBytes = testString.toByteArray()
            val writeSuccess = writeToSharedBuffer(stringBytes)
            val readBytes = readFromSharedBuffer(stringBytes.size)
            val readString = readBytes?.toString(Charsets.UTF_8) ?: "FAILED"
            results["string_test"] = "Write: $writeSuccess, Read: '$readString', Match: ${testString == readString}"
            
            val binaryData = byteArrayOf(0x01, 0x02, 0x03, 0xFF.toByte(), 0xAB.toByte())
            clearBuffer()
            val binaryWriteSuccess = writeToSharedBuffer(binaryData)
            val readBinaryBytes = readFromSharedBuffer(binaryData.size)
            val binaryMatch = readBinaryBytes?.contentEquals(binaryData) ?: false
            results["binary_test"] = "Write: $binaryWriteSuccess, Read: ${readBinaryBytes?.size ?: 0} bytes, Match: $binaryMatch"
            
        } catch (e: Exception) {
            results["error"] = "Exception occurred: ${e.message}"
            Log.e(TAG, "Error during byte transfer tests", e)
        }
        
        return results
    }
    
    fun cleanup() {
        Log.i(TAG, "Cleaning up byte transfer system")
        nativeCleanup()
        isInitialized = false
        Log.i(TAG, "Byte transfer cleanup complete")
    }
    
    fun isInitialized(): Boolean = isInitialized
}