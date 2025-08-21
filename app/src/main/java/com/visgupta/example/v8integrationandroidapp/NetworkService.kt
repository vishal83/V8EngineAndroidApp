package com.visgupta.example.v8integrationandroidapp

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLConnection
import javax.net.ssl.HttpsURLConnection

/**
 * Service for fetching JavaScript code from remote web servers
 * Supports both HTTP and HTTPS protocols with proper error handling
 */
class NetworkService {
    
    companion object {
        private const val TAG = "NetworkService"
        private const val CONNECTION_TIMEOUT = 10000 // 10 seconds
        private const val READ_TIMEOUT = 15000 // 15 seconds
        private const val MAX_CONTENT_LENGTH = 1024 * 1024 // 1MB limit
    }
    
    /**
     * Result class for network operations
     */
    sealed class NetworkResult {
        data class Success(val content: String, val url: String, val contentType: String?) : NetworkResult()
        data class Error(val message: String, val url: String, val statusCode: Int? = null) : NetworkResult()
    }
    
    /**
     * Fetch JavaScript code from a remote URL
     * @param url The URL to fetch JavaScript from
     * @return NetworkResult with either success content or error details
     */
    suspend fun fetchJavaScript(url: String): NetworkResult = withContext(Dispatchers.IO) {
        Log.i(TAG, "Fetching JavaScript from: $url")
        
        try {
            // Validate URL
            if (!isValidUrl(url)) {
                return@withContext NetworkResult.Error("Invalid URL format", url)
            }
            
            val urlObj = URL(url)
            val connection = urlObj.openConnection()
            
            // Configure connection
            when (connection) {
                is HttpsURLConnection -> {
                    connection.connectTimeout = CONNECTION_TIMEOUT
                    connection.readTimeout = READ_TIMEOUT
                    connection.requestMethod = "GET"
                    connection.setRequestProperty("User-Agent", "V8EngineAndroidApp/1.0")
                    connection.setRequestProperty("Accept", "text/javascript, application/javascript, text/plain, */*")
                }
                is HttpURLConnection -> {
                    connection.connectTimeout = CONNECTION_TIMEOUT
                    connection.readTimeout = READ_TIMEOUT
                    connection.requestMethod = "GET"
                    connection.setRequestProperty("User-Agent", "V8EngineAndroidApp/1.0")
                    connection.setRequestProperty("Accept", "text/javascript, application/javascript, text/plain, */*")
                }
            }
            
            // Connect and check response
            connection.connect()
            
            val responseCode = when (connection) {
                is HttpURLConnection -> connection.responseCode
                else -> 200 // Assume success for non-HTTP connections
            }
            
            if (responseCode != HttpURLConnection.HTTP_OK) {
                val errorMessage = when (connection) {
                    is HttpURLConnection -> connection.responseMessage ?: "HTTP Error $responseCode"
                    else -> "Connection failed"
                }
                Log.e(TAG, "HTTP error $responseCode: $errorMessage")
                return@withContext NetworkResult.Error(errorMessage, url, responseCode)
            }
            
            // Check content length
            val contentLength = connection.contentLength
            if (contentLength > MAX_CONTENT_LENGTH) {
                Log.e(TAG, "Content too large: $contentLength bytes (max: $MAX_CONTENT_LENGTH)")
                return@withContext NetworkResult.Error(
                    "Content too large: ${contentLength / 1024}KB (max: ${MAX_CONTENT_LENGTH / 1024}KB)", 
                    url, 
                    responseCode
                )
            }
            
            // Get content type
            val contentType = connection.contentType
            Log.d(TAG, "Content-Type: $contentType")
            
            // Read content
            val content = BufferedReader(InputStreamReader(connection.inputStream)).use { reader ->
                val stringBuilder = StringBuilder()
                var line: String?
                var totalBytes = 0
                
                while (reader.readLine().also { line = it } != null) {
                    stringBuilder.append(line).append('\n')
                    totalBytes += line!!.length + 1
                    
                    // Safety check during reading
                    if (totalBytes > MAX_CONTENT_LENGTH) {
                        Log.e(TAG, "Content exceeded size limit during reading")
                        return@withContext NetworkResult.Error(
                            "Content too large during reading", 
                            url
                        )
                    }
                }
                
                stringBuilder.toString()
            }
            
            Log.i(TAG, "Successfully fetched ${content.length} characters from $url")
            return@withContext NetworkResult.Success(content, url, contentType)
            
        } catch (e: java.net.SocketTimeoutException) {
            val error = "Connection timeout: ${e.message}"
            Log.e(TAG, error, e)
            return@withContext NetworkResult.Error(error, url)
        } catch (e: java.net.UnknownHostException) {
            val error = "Unknown host: ${e.message}"
            Log.e(TAG, error, e)
            return@withContext NetworkResult.Error(error, url)
        } catch (e: java.net.ConnectException) {
            val error = "Connection failed: ${e.message}"
            Log.e(TAG, error, e)
            return@withContext NetworkResult.Error(error, url)
        } catch (e: javax.net.ssl.SSLException) {
            val error = "SSL/TLS error: ${e.message}"
            Log.e(TAG, error, e)
            return@withContext NetworkResult.Error(error, url)
        } catch (e: java.io.IOException) {
            val error = "IO error: ${e.message}"
            Log.e(TAG, error, e)
            return@withContext NetworkResult.Error(error, url)
        } catch (e: Exception) {
            val error = "Unexpected error: ${e.message}"
            Log.e(TAG, error, e)
            return@withContext NetworkResult.Error(error, url)
        }
    }
    
    /**
     * Validate URL format
     */
    private fun isValidUrl(url: String): Boolean {
        return try {
            val urlObj = URL(url)
            val protocol = urlObj.protocol.lowercase()
            protocol == "http" || protocol == "https"
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Get suggested file name from URL
     */
    fun getFileNameFromUrl(url: String): String {
        return try {
            val urlObj = URL(url)
            val path = urlObj.path
            if (path.isNotEmpty() && path.contains("/")) {
                val fileName = path.substringAfterLast("/")
                if (fileName.isNotEmpty()) fileName else "remote_script.js"
            } else {
                "remote_script.js"
            }
        } catch (e: Exception) {
            "remote_script.js"
        }
    }
    
    /**
     * Check if content appears to be JavaScript based on content type or URL
     */
    fun isJavaScriptContent(contentType: String?, url: String): Boolean {
        // Check content type
        contentType?.let { type ->
            val lowerType = type.lowercase()
            if (lowerType.contains("javascript") || 
                lowerType.contains("application/js") ||
                lowerType.contains("text/js")) {
                return true
            }
        }
        
        // Check URL extension
        val lowerUrl = url.lowercase()
        return lowerUrl.endsWith(".js") || 
               lowerUrl.endsWith(".mjs") || 
               lowerUrl.contains(".js?") ||
               lowerUrl.contains(".mjs?")
    }
    
    /**
     * Sanitize JavaScript content (basic safety checks)
     */
    fun sanitizeJavaScript(content: String): String {
        // Remove potential harmful patterns (basic implementation)
        var sanitized = content
        
        // Remove HTML script tags if present
        sanitized = sanitized.replace(Regex("<script[^>]*>"), "")
        sanitized = sanitized.replace(Regex("</script>"), "")
        
        // Log if suspicious patterns are found
        if (content.contains("eval(") || content.contains("Function(")) {
            Log.w(TAG, "JavaScript content contains potentially unsafe eval() or Function() calls")
        }
        
        return sanitized
    }
}
