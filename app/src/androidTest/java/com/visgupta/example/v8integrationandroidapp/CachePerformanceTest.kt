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
import kotlin.system.measureTimeMillis

/**
 * Performance tests for the caching system.
 * These tests measure execution times and verify performance improvements.
 */
@RunWith(AndroidJUnit4::class)
class CachePerformanceTest {

    private lateinit var mockWebServer: MockWebServer
    private lateinit var quickJSBridge: QuickJSBridge
    private lateinit var cacheService: CacheService

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        
        mockWebServer = MockWebServer()
        mockWebServer.start()
        
        quickJSBridge = QuickJSBridge(context)
        cacheService = CacheService(context)
        
        // Initialize QuickJS
        quickJSBridge.initialize()
        
        // Clear cache for clean test state
        cacheService.clearCache()
    }

    @After
    fun tearDown() {
        mockWebServer.shutdown()
        cacheService.clearCache()
    }

    @Test
    fun testCachePerformanceImprovement() = runBlocking {
        val jsContent = """
            // Performance test script
            function fibonacci(n) {
                if (n <= 1) return n;
                return fibonacci(n - 1) + fibonacci(n - 2);
            }
            
            const result = {
                fib15: fibonacci(15),
                timestamp: Date.now(),
                message: "Performance test completed"
            };
            
            JSON.stringify(result);
        """.trimIndent()

        // Add network delay to simulate real-world conditions
        val mockResponse = MockResponse()
            .setResponseCode(200)
            .setHeader("Content-Type", "text/javascript")
            .setHeader("ETag", "\"perf-test-123\"")
            .setBodyDelay(200, TimeUnit.MILLISECONDS) // 200ms network delay
            .setBody(jsContent)

        mockWebServer.enqueue(mockResponse)

        val url = mockWebServer.url("/performance-test.js").toString()
        
        // Measure first execution (network + cache + execution)
        val firstExecutionTime = measureFirstExecution(url)
        println("First execution time: ${firstExecutionTime}ms")
        
        // Measure second execution (cache hit + execution)
        val secondExecutionTime = measureSecondExecution(url)
        println("Second execution time: ${secondExecutionTime}ms")
        
        // Verify performance improvement
        assertTrue("Second execution should be faster than first", 
                  secondExecutionTime < firstExecutionTime)
        
        val improvementPercent = ((firstExecutionTime - secondExecutionTime).toDouble() / firstExecutionTime) * 100
        println("Performance improvement: ${improvementPercent.toInt()}%")
        
        assertTrue("Should have at least 30% improvement", improvementPercent > 30)
        assertTrue("Cached execution should be under 100ms", secondExecutionTime < 100)
    }

    private suspend fun measureFirstExecution(url: String): Long {
        val latch = CountDownLatch(1)
        var executionTime = 0L
        
        val startTime = System.currentTimeMillis()
        
        val callback = object : QuickJSBridge.RemoteExecutionCallback {
            override fun onProgress(message: String) {}
            override fun onSuccess(result: QuickJSBridge.RemoteExecutionResult) {
                executionTime = System.currentTimeMillis() - startTime
                latch.countDown()
            }
            override fun onError(error: String) {
                fail("First execution should not fail: $error")
                latch.countDown()
            }
        }
        
        quickJSBridge.executeRemoteJavaScript(url, callback)
        assertTrue("First execution should complete", latch.await(10, TimeUnit.SECONDS))
        
        return executionTime
    }

    private suspend fun measureSecondExecution(url: String): Long {
        val latch = CountDownLatch(1)
        var executionTime = 0L
        
        val startTime = System.currentTimeMillis()
        
        val callback = object : QuickJSBridge.RemoteExecutionCallback {
            override fun onProgress(message: String) {}
            override fun onSuccess(result: QuickJSBridge.RemoteExecutionResult) {
                executionTime = System.currentTimeMillis() - startTime
                latch.countDown()
            }
            override fun onError(error: String) {
                fail("Second execution should not fail: $error")
                latch.countDown()
            }
        }
        
        quickJSBridge.executeRemoteJavaScript(url, callback)
        assertTrue("Second execution should complete", latch.await(5, TimeUnit.SECONDS))
        
        return executionTime
    }

    @Test
    fun testCacheOperationPerformance() = runBlocking {
        val urls = (1..100).map { "https://example.com/test-$it.js" }
        val jsContent = "console.log('Cache performance test');"
        
        // Measure cache write performance
        val writeTime = measureTimeMillis {
            runBlocking {
                urls.forEach { url ->
                    cacheService.cacheEntry(url, jsContent, contentType = "text/javascript")
                }
            }
        }
        
        println("Cache write time for 100 entries: ${writeTime}ms")
        assertTrue("Write time should be reasonable", writeTime < 1000)
        
        // Measure cache read performance
        val readTime = measureTimeMillis {
            runBlocking {
                urls.forEach { url ->
                    cacheService.getCachedEntry(url)
                }
            }
        }
        
        println("Cache read time for 100 entries: ${readTime}ms")
        assertTrue("Read time should be very fast", readTime < 500)
        
        // Verify all entries were cached
        val stats = cacheService.getCacheStats()
        assertEquals("Should have cached 100 entries", 100, stats.totalEntries)
    }

    @Test
    fun testMemoryCachePerformance() = runBlocking {
        val testSize = 1000
        val jsContent = "console.log('Memory cache test');"
        
        // Test memory cache performance with many entries
        val urls = (1..testSize).map { "https://example.com/memory-test-$it.js" }
        
        // Cache all entries
        val cacheTime = measureTimeMillis {
            runBlocking {
                urls.forEach { url ->
                    cacheService.cacheEntry(url, jsContent, contentType = "text/javascript")
                }
            }
        }
        
        println("Time to cache $testSize entries: ${cacheTime}ms")
        
        // Measure random access performance
        val randomUrls = urls.shuffled().take(100)
        val accessTime = measureTimeMillis {
            runBlocking {
                randomUrls.forEach { url ->
                    cacheService.getCachedEntry(url)
                }
            }
        }
        
        println("Time for 100 random cache accesses: ${accessTime}ms")
        assertTrue("Random access should be fast", accessTime < 100)
        
        val stats = cacheService.getCacheStats()
        assertTrue("Should have cached many entries", stats.totalEntries > 900)
        assertTrue("Hit rate should be very high", stats.hitRate > 0.9)
    }

    @Test
    fun testConcurrentPerformance() = runBlocking {
        val jsContent = "console.log('Concurrent performance test');"
        val url = "https://example.com/concurrent-test.js"
        
        // Cache the entry first
        cacheService.cacheEntry(url, jsContent, contentType = "text/javascript")
        
        val threadCount = 20
        val operationsPerThread = 50
        val results = mutableListOf<Long>()
        val threads = mutableListOf<Thread>()
        
        val startTime = System.currentTimeMillis()
        
        repeat(threadCount) { threadId ->
            val thread = Thread {
                val threadStartTime = System.currentTimeMillis()
                repeat(operationsPerThread) {
                    runBlocking {
                        cacheService.getCachedEntry(url)
                    }
                }
                val threadEndTime = System.currentTimeMillis()
                synchronized(results) {
                    results.add(threadEndTime - threadStartTime)
                }
            }
            threads.add(thread)
            thread.start()
        }
        
        // Wait for all threads
        threads.forEach { it.join() }
        
        val totalTime = System.currentTimeMillis() - startTime
        val avgThreadTime = results.average()
        val totalOperations = threadCount * operationsPerThread
        
        println("Concurrent performance test results:")
        println("- Total time: ${totalTime}ms")
        println("- Average thread time: ${avgThreadTime.toInt()}ms")
        println("- Total operations: $totalOperations")
        println("- Operations per second: ${(totalOperations * 1000.0 / totalTime).toInt()}")
        
        assertTrue("Concurrent operations should complete quickly", totalTime < 5000)
        assertTrue("Should achieve high throughput", totalOperations * 1000.0 / totalTime > 1000)
    }

    @Test
    fun testLargeContentPerformance() = runBlocking {
        // Test performance with large JavaScript files
        val sizes = listOf(1, 10, 50, 100) // KB
        
        sizes.forEach { sizeKB ->
            val content = "console.log('test');".repeat(sizeKB * 50) // Approximate KB
            val url = "https://example.com/large-$sizeKB.js"
            
            // Measure cache time
            val cacheTime = measureTimeMillis {
                runBlocking {
                    cacheService.cacheEntry(url, content, contentType = "text/javascript")
                }
            }
            
            // Measure retrieval time
            val retrieveTime = measureTimeMillis {
                runBlocking {
                    cacheService.getCachedEntry(url)
                }
            }
            
            println("${sizeKB}KB file - Cache: ${cacheTime}ms, Retrieve: ${retrieveTime}ms")
            
            assertTrue("Cache time should be reasonable for ${sizeKB}KB", cacheTime < 1000)
            assertTrue("Retrieval should be very fast for ${sizeKB}KB", retrieveTime < 100)
        }
    }

    @Test
    fun testCacheEvictionPerformance() = runBlocking {
        val maxEntries = 150 // Exceed typical memory cache limit
        val jsContent = "console.log('Eviction test');"
        
        // Fill cache beyond typical limits
        val cacheTime = measureTimeMillis {
            runBlocking {
                repeat(maxEntries) { i ->
                    val url = "https://example.com/eviction-test-$i.js"
                    cacheService.cacheEntry(url, jsContent, contentType = "text/javascript")
                }
            }
        }
        
        println("Time to cache $maxEntries entries: ${cacheTime}ms")
        
        val stats = cacheService.getCacheStats()
        println("Final cache stats: ${stats.totalEntries} entries, ${stats.memoryEntries} in memory")
        
        // Test access performance after eviction
        val accessTime = measureTimeMillis {
            runBlocking {
                repeat(50) { i ->
                    val url = "https://example.com/eviction-test-$i.js"
                    cacheService.getCachedEntry(url)
                }
            }
        }
        
        println("Time for 50 cache accesses after eviction: ${accessTime}ms")
        assertTrue("Cache should handle eviction gracefully", accessTime < 500)
    }

    @Test
    fun testBytecodeCachePerformance() = runBlocking {
        val url = "https://example.com/bytecode-test.js"
        val bytecode = ByteArray(10000) { it.toByte() } // 10KB of mock bytecode
        
        // Measure bytecode cache performance
        val cacheTime = measureTimeMillis {
            runBlocking {
                cacheService.cacheBytecode(url, bytecode)
            }
        }
        
        val retrieveTime = measureTimeMillis {
            runBlocking {
                cacheService.getCachedBytecode(url)
            }
        }
        
        println("Bytecode cache time: ${cacheTime}ms, retrieve time: ${retrieveTime}ms")
        
        assertTrue("Bytecode caching should be fast", cacheTime < 100)
        assertTrue("Bytecode retrieval should be very fast", retrieveTime < 50)
        
        // Verify bytecode was cached correctly
        val cachedBytecode = cacheService.getCachedBytecode(url)
        assertNotNull("Bytecode should be cached", cachedBytecode)
        assertArrayEquals("Cached bytecode should match", bytecode, cachedBytecode)
    }

    @Test
    fun testCacheStatsPerformance() = runBlocking {
        // Cache many entries
        repeat(500) { i ->
            val url = "https://example.com/stats-test-$i.js"
            cacheService.cacheEntry(url, "console.log($i);", contentType = "text/javascript")
        }
        
        // Generate some hits and misses
        repeat(200) { i ->
            cacheService.getCachedEntry("https://example.com/stats-test-$i.js") // hits
        }
        repeat(100) { i ->
            cacheService.getCachedEntry("https://example.com/missing-$i.js") // misses
        }
        
        // Measure stats calculation performance
        val statsTime = measureTimeMillis {
            repeat(100) {
                cacheService.getCacheStats()
            }
        }
        
        println("Time for 100 stats calculations: ${statsTime}ms")
        assertTrue("Stats calculation should be fast", statsTime < 1000)
        
        val stats = cacheService.getCacheStats()
        assertTrue("Should have correct hit rate", stats.hitRate > 0.6 && stats.hitRate < 0.7)
        assertEquals("Should have correct entry count", 500, stats.totalEntries)
    }
}
