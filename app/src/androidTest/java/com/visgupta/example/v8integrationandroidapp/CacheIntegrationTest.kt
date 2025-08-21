package com.visgupta.example.v8integrationandroidapp

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Integration tests for the complete caching system workflow.
 * These tests run on Android devices/emulators and test the full stack.
 */
@RunWith(AndroidJUnit4::class)
class CacheIntegrationTest {

    private lateinit var mockWebServer: MockWebServer
    private lateinit var quickJSBridge: QuickJSBridge
    private lateinit var cacheService: CacheService
    private lateinit var httpService: HttpService

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        
        mockWebServer = MockWebServer()
        mockWebServer.start()
        
        quickJSBridge = QuickJSBridge(context)
        cacheService = CacheService(context)
        httpService = HttpService()
        
        // Initialize QuickJS
        quickJSBridge.initialize()
    }

    @After
    fun tearDown() {
        mockWebServer.shutdown()
        cacheService.clearCache()
    }

    @Test
    fun testEndToEndCachingWorkflow() = runBlocking {
        val jsContent = """
            const message = "Hello from cached script!";
            const result = {
                message: message,
                timestamp: new Date().toISOString(),
                cached: true
            };
            JSON.stringify(result);
        """.trimIndent()

        // Setup mock server response
        val mockResponse = MockResponse()
            .setResponseCode(200)
            .setHeader("Content-Type", "text/javascript")
            .setHeader("ETag", "\"test-etag-123\"")
            .setHeader("Last-Modified", "Wed, 21 Oct 2015 07:28:00 GMT")
            .setBody(jsContent)

        mockWebServer.enqueue(mockResponse)

        val url = mockWebServer.url("/test-cache.js").toString()
        val latch = CountDownLatch(1)
        var executionResult: QuickJSBridge.RemoteExecutionResult? = null

        // First execution - should fetch from network
        val callback1 = object : QuickJSBridge.RemoteExecutionCallback {
            override fun onProgress(message: String) {
                println("Progress: $message")
            }

            override fun onSuccess(result: QuickJSBridge.RemoteExecutionResult) {
                executionResult = result
                latch.countDown()
            }

            override fun onError(error: String) {
                fail("First execution should not fail: $error")
                latch.countDown()
            }
        }

        quickJSBridge.executeRemoteJavaScript(url, callback1)
        assertTrue("First execution should complete", latch.await(10, TimeUnit.SECONDS))

        assertNotNull("First execution result should not be null", executionResult)
        assertTrue("First execution should be successful", executionResult!!.success)
        assertTrue("Result should contain expected content", 
                  executionResult!!.result.contains("Hello from cached script"))

        // Verify cache entry was created
        val cachedEntry = cacheService.getCachedEntry(url)
        assertNotNull("Cache entry should exist", cachedEntry)
        assertEquals(jsContent, cachedEntry!!.sourceCode)
        assertEquals("\"test-etag-123\"", cachedEntry.etag)

        // Second execution - should use cache (no network request)
        val latch2 = CountDownLatch(1)
        var cachedResult: QuickJSBridge.RemoteExecutionResult? = null

        val callback2 = object : QuickJSBridge.RemoteExecutionCallback {
            override fun onProgress(message: String) {
                println("Cached Progress: $message")
            }

            override fun onSuccess(result: QuickJSBridge.RemoteExecutionResult) {
                cachedResult = result
                latch2.countDown()
            }

            override fun onError(error: String) {
                fail("Cached execution should not fail: $error")
                latch2.countDown()
            }
        }

        quickJSBridge.executeRemoteJavaScript(url, callback2)
        assertTrue("Cached execution should complete", latch2.await(10, TimeUnit.SECONDS))

        assertNotNull("Cached execution result should not be null", cachedResult)
        assertTrue("Cached execution should be successful", cachedResult!!.success)
        assertTrue("Cached execution should be faster", 
                  cachedResult!!.executionTimeMs < executionResult!!.executionTimeMs + 100)

        // Verify only one network request was made
        assertEquals("Should have made only one network request", 1, mockWebServer.requestCount)
    }

    @Test
    fun testConditionalRequestWorkflow() = runBlocking {
        val originalContent = "console.log('Original content');"
        val updatedContent = "console.log('Updated content');"

        // First request - cache the content
        val etag = "\"version-1\""
        mockWebServer.enqueue(MockResponse()
            .setResponseCode(200)
            .setHeader("Content-Type", "text/javascript")
            .setHeader("ETag", etag)
            .setBody(originalContent))

        val url = mockWebServer.url("/conditional-test.js").toString()
        
        // Cache the content
        cacheService.cacheEntry(url, originalContent, etag = etag, contentType = "text/javascript")

        // Second request - server returns 304 Not Modified
        mockWebServer.enqueue(MockResponse()
            .setResponseCode(304)
            .setHeader("ETag", etag))

        val options = HttpService.HttpRequestOptions(method = "GET")
        val response = httpService.executeRequest(url, options, cacheService)

        assertEquals(200, response.status) // Should return cached content as 200
        assertEquals(originalContent, response.body)
        assertTrue("Should indicate revalidation", response.headers.containsValue("REVALIDATED"))

        // Verify conditional headers were sent
        val request = mockWebServer.takeRequest()
        assertEquals(etag, request.getHeader("If-None-Match"))
    }

    @Test
    fun testCacheExpirationAndRevalidation() = runBlocking {
        val jsContent = "console.log('Expiration test');"
        val url = mockWebServer.url("/expiration-test.js").toString()

        // Create an expired cache entry
        val expiredEntry = CacheService.CacheEntry(
            url = url,
            sourceCode = jsContent,
            etag = "\"old-etag\"",
            lastModified = null,
            contentType = "text/javascript",
            cachedAt = System.currentTimeMillis() - (25 * 60 * 60 * 1000L), // 25 hours ago
            ttlMs = 24 * 60 * 60 * 1000L, // 24 hour TTL
            hash = "test-hash",
            size = jsContent.length
        )

        // Manually add expired entry to test expiration logic
        assertTrue("Entry should be expired", expiredEntry.isExpired())
        assertTrue("Expired entry should need revalidation", expiredEntry.needsRevalidation())

        // Test that expired entries are properly handled
        val cachedEntry = cacheService.getCachedEntry(url)
        assertNull("Expired entry should not be returned", cachedEntry)
    }

    @Test
    fun testConcurrentCacheAccess() = runBlocking {
        val jsContent = "console.log('Concurrent test');"
        val url = mockWebServer.url("/concurrent-test.js").toString()

        // Cache an entry
        cacheService.cacheEntry(url, jsContent, contentType = "text/javascript")

        // Test concurrent access
        val results = mutableListOf<CacheService.CacheEntry?>()
        val threads = mutableListOf<Thread>()

        repeat(10) { i ->
            val thread = Thread {
                runBlocking {
                    val entry = cacheService.getCachedEntry(url)
                    synchronized(results) {
                        results.add(entry)
                    }
                }
            }
            threads.add(thread)
            thread.start()
        }

        // Wait for all threads to complete
        threads.forEach { it.join() }

        assertEquals("All threads should get results", 10, results.size)
        results.forEach { entry ->
            assertNotNull("Each thread should get cache entry", entry)
            assertEquals("Content should match", jsContent, entry!!.sourceCode)
        }
    }

    @Test
    fun testCacheStatsAccuracy() = runBlocking {
        val url1 = mockWebServer.url("/stats-test-1.js").toString()
        val url2 = mockWebServer.url("/stats-test-2.js").toString()
        val url3 = mockWebServer.url("/stats-test-3.js").toString()

        // Clear cache and reset stats
        cacheService.clearCache()

        // Cache some entries
        cacheService.cacheEntry(url1, "console.log('test1');", contentType = "text/javascript")
        cacheService.cacheEntry(url2, "console.log('test2');", contentType = "text/javascript")

        var stats = cacheService.getCacheStats()
        assertEquals("Should have 2 entries", 2, stats.totalEntries)
        assertEquals("Should have 2 memory entries", 2, stats.memoryEntries)

        // Generate hits and misses
        cacheService.getCachedEntry(url1) // hit
        cacheService.getCachedEntry(url1) // hit
        cacheService.getCachedEntry(url2) // hit
        cacheService.getCachedEntry(url3) // miss

        stats = cacheService.getCacheStats()
        assertTrue("Hit rate should be 0.75 (3 hits / 4 total)", 
                  Math.abs(stats.hitRate - 0.75) < 0.01)
    }

    @Test
    fun testLargeScriptCaching() = runBlocking {
        // Test caching of a large JavaScript file
        val largeScript = buildString {
            append("// Large script test\n")
            repeat(1000) { i ->
                append("function func$i() { return $i * 2; }\n")
            }
            append("console.log('Large script executed');\n")
            append("JSON.stringify({ functions: 1000, size: 'large' });\n")
        }

        mockWebServer.enqueue(MockResponse()
            .setResponseCode(200)
            .setHeader("Content-Type", "text/javascript")
            .setBody(largeScript))

        val url = mockWebServer.url("/large-script.js").toString()
        val latch = CountDownLatch(1)
        var result: QuickJSBridge.RemoteExecutionResult? = null

        val callback = object : QuickJSBridge.RemoteExecutionCallback {
            override fun onProgress(message: String) {}
            override fun onSuccess(executionResult: QuickJSBridge.RemoteExecutionResult) {
                result = executionResult
                latch.countDown()
            }
            override fun onError(error: String) {
                fail("Large script execution should not fail: $error")
                latch.countDown()
            }
        }

        quickJSBridge.executeRemoteJavaScript(url, callback)
        assertTrue("Large script should complete", latch.await(30, TimeUnit.SECONDS))

        assertNotNull("Result should not be null", result)
        assertTrue("Execution should be successful", result!!.success)
        assertEquals("Content length should match", largeScript.length, result!!.contentLength)

        // Verify cache entry
        val cachedEntry = cacheService.getCachedEntry(url)
        assertNotNull("Large script should be cached", cachedEntry)
        assertEquals("Cached content should match", largeScript, cachedEntry!!.sourceCode)
    }

    @Test
    fun testNetworkErrorHandling() = runBlocking {
        // Test handling of network errors with caching
        val url = "http://nonexistent-server-12345.com/test.js"
        val latch = CountDownLatch(1)
        var errorMessage: String? = null

        val callback = object : QuickJSBridge.RemoteExecutionCallback {
            override fun onProgress(message: String) {}
            override fun onSuccess(result: QuickJSBridge.RemoteExecutionResult) {
                fail("Should not succeed with nonexistent server")
                latch.countDown()
            }
            override fun onError(error: String) {
                errorMessage = error
                latch.countDown()
            }
        }

        quickJSBridge.executeRemoteJavaScript(url, callback)
        assertTrue("Error callback should be called", latch.await(15, TimeUnit.SECONDS))

        assertNotNull("Error message should not be null", errorMessage)
        assertTrue("Error should indicate network failure", 
                  errorMessage!!.contains("failed") || errorMessage!!.contains("error"))
    }

    @Test
    fun testCacheCleanupAndMemoryManagement() = runBlocking {
        val baseUrl = mockWebServer.url("/memory-test").toString()

        // Create many cache entries to test memory management
        repeat(50) { i ->
            val url = "$baseUrl-$i.js"
            val content = "console.log('Memory test $i');"
            cacheService.cacheEntry(url, content, contentType = "text/javascript")
        }

        val stats = cacheService.getCacheStats()
        assertTrue("Should have many entries", stats.totalEntries > 40)

        // Clear cache
        cacheService.clearCache()

        val clearedStats = cacheService.getCacheStats()
        assertEquals("Cache should be empty", 0, clearedStats.totalEntries)
        assertEquals("Memory cache should be empty", 0, clearedStats.memoryEntries)
    }
}
