package com.visgupta.example.v8integrationandroidapp

import android.util.Log
import kotlinx.coroutines.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * HTTP service for JavaScript fetch/XMLHttpRequest polyfills
 * Uses OkHttp for actual networking operations
 */
class HttpService {
    private val TAG = "HttpService"
    
    // OkHttp client with reasonable timeouts
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    /**
     * HTTP response data class for JavaScript consumption
     */
    data class HttpResponse(
        val status: Int,
        val statusText: String,
        val headers: Map<String, String>,
        val body: String,
        val ok: Boolean = status in 200..299,
        val redirected: Boolean = false,
        val url: String,
        val type: String = "basic"
    )

    /**
     * HTTP request options for JavaScript fetch API
     */
    data class HttpRequestOptions(
        val method: String = "GET",
        val headers: Map<String, String> = emptyMap(),
        val body: String? = null,
        val timeout: Long = 30000,
        val redirect: String = "follow", // follow, error, manual
        val credentials: String = "same-origin" // omit, same-origin, include
    )

    /**
     * Execute HTTP request asynchronously
     * This is the main method called from native code
     */
    suspend fun executeRequest(
        url: String,
        options: HttpRequestOptions
    ): HttpResponse = withContext(Dispatchers.IO) {
        Log.d(TAG, "Executing HTTP request: ${options.method} $url")
        
        try {
            val requestBuilder = Request.Builder()
                .url(url)
                .method(
                    options.method,
                    if (options.body != null && options.method != "GET" && options.method != "HEAD") {
                        options.body.toRequestBody("application/json; charset=utf-8".toMediaType())
                    } else null
                )

            // Add headers
            options.headers.forEach { (key, value) ->
                requestBuilder.addHeader(key, value)
            }

            val request = requestBuilder.build()
            val response = client.newCall(request).execute()

            val responseHeaders = mutableMapOf<String, String>()
            response.headers.forEach { pair ->
                responseHeaders[pair.first] = pair.second
            }

            val responseBody = response.body?.string() ?: ""
            
            Log.d(TAG, "HTTP response: ${response.code} ${response.message}")
            
            HttpResponse(
                status = response.code,
                statusText = response.message,
                headers = responseHeaders,
                body = responseBody,
                url = response.request.url.toString(),
                redirected = response.priorResponse != null
            )
            
        } catch (e: IOException) {
            Log.e(TAG, "HTTP request failed", e)
            HttpResponse(
                status = 0,
                statusText = "Network Error",
                headers = emptyMap(),
                body = "",
                url = url
            )
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error during HTTP request", e)
            HttpResponse(
                status = 0,
                statusText = "Unknown Error: ${e.message}",
                headers = emptyMap(),
                body = "",
                url = url
            )
        }
    }

    /**
     * Parse JavaScript options object to HttpRequestOptions
     */
    fun parseRequestOptions(optionsJson: String): HttpRequestOptions {
        return try {
            val json = JSONObject(optionsJson)
            val headers = mutableMapOf<String, String>()
            
            if (json.has("headers")) {
                val headersJson = json.getJSONObject("headers")
                headersJson.keys().forEach { key ->
                    headers[key] = headersJson.getString(key)
                }
            }

            HttpRequestOptions(
                method = json.optString("method", "GET").uppercase(),
                headers = headers,
                body = if (json.has("body")) json.getString("body") else null,
                timeout = json.optLong("timeout", 30000),
                redirect = json.optString("redirect", "follow"),
                credentials = json.optString("credentials", "same-origin")
            )
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse request options, using defaults", e)
            HttpRequestOptions()
        }
    }

    /**
     * Convert HttpResponse to JSON string for JavaScript consumption
     */
    fun responseToJson(response: HttpResponse): String {
        val json = JSONObject()
        json.put("status", response.status)
        json.put("statusText", response.statusText)
        json.put("ok", response.ok)
        json.put("redirected", response.redirected)
        json.put("url", response.url)
        json.put("type", response.type)
        json.put("body", response.body)
        
        val headersJson = JSONObject()
        response.headers.forEach { (key, value) ->
            headersJson.put(key, value)
        }
        json.put("headers", headersJson)
        
        return json.toString()
    }

    /**
     * Cleanup resources
     */
    fun cleanup() {
        client.dispatcher.executorService.shutdown()
        client.connectionPool.evictAll()
        Log.d(TAG, "HttpService cleanup complete")
    }
}
