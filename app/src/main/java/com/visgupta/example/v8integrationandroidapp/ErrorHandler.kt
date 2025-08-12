package com.visgupta.example.v8integrationandroidapp

import android.util.Log

/**
 * Centralized error handling and logging utility
 */
object ErrorHandler {
    
    private const val TAG = "ErrorHandler"
    
    /**
     * Handle initialization errors with standardized logging
     */
    fun handleInitializationError(component: String, error: Throwable? = null): String {
        val message = "❌ Failed to initialize $component"
        if (error != null) {
            Log.e(TAG, message, error)
            return "$message: ${error.message}"
        } else {
            Log.e(TAG, message)
            return message
        }
    }
    
    /**
     * Handle operation errors with standardized logging
     */
    fun handleOperationError(operation: String, error: Throwable? = null): String {
        val message = "❌ $operation failed"
        if (error != null) {
            Log.e(TAG, message, error)
            return "$message: ${error.message}"
        } else {
            Log.e(TAG, message)
            return message
        }
    }
    
    /**
     * Handle success with standardized logging
     */
    fun handleSuccess(operation: String): String {
        val message = "✅ $operation successful"
        Log.i(TAG, message)
        return message
    }
    
    /**
     * Validate buffer size parameters
     */
    fun validateBufferSize(size: Int, maxSize: Int = 100 * 1024 * 1024): String? {
        return when {
            size <= 0 -> "Buffer size must be positive"
            size > maxSize -> "Buffer size too large (max ${maxSize / (1024 * 1024)}MB)"
            else -> null
        }
    }
    
    /**
     * Safe execution wrapper with error handling
     */
    inline fun <T> safeExecute(operation: String, block: () -> T): Result<T> {
        return try {
            val result = block()
            Log.i(TAG, "✅ $operation completed successfully")
            Result.success(result)
        } catch (e: Exception) {
            val error = "Unexpected error during $operation"
            Log.e(TAG, error, e)
            Result.failure(Exception("❌ $error: ${e.message}"))
        }
    }
}