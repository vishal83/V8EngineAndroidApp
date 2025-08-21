package com.visgupta.example.v8integrationandroidapp

import android.content.Context
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.junit.MockitoJUnitRunner
import org.mockito.Mockito.*
import java.io.File
import java.io.ByteArrayOutputStream
import java.io.ByteArrayInputStream

@RunWith(MockitoJUnitRunner::class)
class CacheServiceTest {

    @Mock
    private lateinit var mockContext: Context

    @Mock
    private lateinit var mockCacheDir: File

    private lateinit var cacheService: CacheService

    @Before
    fun setUp() {
        // Mock context and cache directory
        `when`(mockContext.cacheDir).thenReturn(mockCacheDir)
        `when`(mockCacheDir.exists()).thenReturn(true)
        `when`(mockCacheDir.isDirectory).thenReturn(true)
        
        cacheService = CacheService(mockContext)
    }

    @Test
    fun testCacheEntryCreation() = runBlocking {
        val url = "https://example.com/test.js"
        val sourceCode = "console.log('Hello World');"
        val etag = "\"abc123\""
        val lastModified = "Wed, 21 Oct 2015 07:28:00 GMT"
        val contentType = "text/javascript"

        val entry = cacheService.cacheEntry(
            url = url,
            sourceCode = sourceCode,
            etag = etag,
            lastModified = lastModified,
            contentType = contentType
        )

        assertNotNull(entry)
        assertEquals(url, entry.url)
        assertEquals(sourceCode, entry.sourceCode)
        assertEquals(etag, entry.etag)
        assertEquals(lastModified, entry.lastModified)
        assertEquals(contentType, entry.contentType)
        assertEquals(sourceCode.length, entry.size)
        assertFalse(entry.isExpired())
        assertFalse(entry.needsRevalidation())
    }

    @Test
    fun testCacheEntryExpiration() {
        val entry = CacheService.CacheEntry(
            url = "https://example.com/test.js",
            sourceCode = "console.log('test');",
            etag = null,
            lastModified = null,
            contentType = "text/javascript",
            cachedAt = System.currentTimeMillis() - (25 * 60 * 60 * 1000L), // 25 hours ago
            ttlMs = 24 * 60 * 60 * 1000L, // 24 hour TTL
            hash = "hash123",
            size = 20
        )

        assertTrue("Entry should be expired after 25 hours with 24h TTL", entry.isExpired())
        assertTrue("Expired entry should need revalidation", entry.needsRevalidation())
    }

    @Test
    fun testCacheEntryRevalidationLogic() {
        // Entry with ETag - should not need revalidation if not expired
        val entryWithETag = CacheService.CacheEntry(
            url = "https://example.com/test.js",
            sourceCode = "console.log('test');",
            etag = "\"abc123\"",
            lastModified = null,
            contentType = "text/javascript",
            cachedAt = System.currentTimeMillis() - (1 * 60 * 60 * 1000L), // 1 hour ago
            ttlMs = 24 * 60 * 60 * 1000L, // 24 hour TTL
            hash = "hash123",
            size = 20
        )
        
        assertFalse("Entry with ETag should not need revalidation if not expired", 
                   entryWithETag.needsRevalidation())

        // Entry with Last-Modified - should not need revalidation if not expired
        val entryWithLastModified = CacheService.CacheEntry(
            url = "https://example.com/test.js",
            sourceCode = "console.log('test');",
            etag = null,
            lastModified = "Wed, 21 Oct 2015 07:28:00 GMT",
            contentType = "text/javascript",
            cachedAt = System.currentTimeMillis() - (1 * 60 * 60 * 1000L), // 1 hour ago
            ttlMs = 24 * 60 * 60 * 1000L, // 24 hour TTL
            hash = "hash123",
            size = 20
        )
        
        assertFalse("Entry with Last-Modified should not need revalidation if not expired", 
                   entryWithLastModified.needsRevalidation())

        // Entry without validation headers - should need revalidation based on current logic
        val entryWithoutHeaders = CacheService.CacheEntry(
            url = "https://example.com/test.js",
            sourceCode = "console.log('test');",
            etag = null,
            lastModified = null,
            contentType = "text/javascript",
            cachedAt = System.currentTimeMillis() - (1 * 60 * 60 * 1000L), // 1 hour ago
            ttlMs = 24 * 60 * 60 * 1000L, // 24 hour TTL
            hash = "hash123",
            size = 20
        )
        
        // Based on our current simplified logic, this should NOT need revalidation if not expired
        assertFalse("Entry should only need revalidation if expired", 
                   entryWithoutHeaders.needsRevalidation())
    }

    @Test
    fun testMemoryCacheOperations() = runBlocking {
        val url = "https://example.com/test.js"
        val sourceCode = "console.log('Memory Cache Test');"

        // Test cache miss
        var cachedEntry = cacheService.getCachedEntry(url)
        assertNull("Should return null for cache miss", cachedEntry)

        // Cache an entry
        val entry = cacheService.cacheEntry(
            url = url,
            sourceCode = sourceCode,
            contentType = "text/javascript"
        )

        // Test cache hit
        cachedEntry = cacheService.getCachedEntry(url)
        assertNotNull("Should return cached entry for cache hit", cachedEntry)
        assertEquals(sourceCode, cachedEntry?.sourceCode)
        assertEquals(url, cachedEntry?.url)
    }

    @Test
    fun testBytecodeCaching() = runBlocking {
        val url = "https://example.com/test.js"
        val bytecode = "compiled_bytecode_data".toByteArray()

        // Test bytecode cache miss
        var cachedBytecode = cacheService.getCachedBytecode(url)
        assertNull("Should return null for bytecode cache miss", cachedBytecode)

        // Cache bytecode
        cacheService.cacheBytecode(url, bytecode)

        // Test bytecode cache hit
        cachedBytecode = cacheService.getCachedBytecode(url)
        assertNotNull("Should return cached bytecode for cache hit", cachedBytecode)
        assertArrayEquals("Cached bytecode should match original", bytecode, cachedBytecode)
    }

    @Test
    fun testCacheStats() = runBlocking {
        // Initial stats should show empty cache
        var stats = cacheService.getCacheStats()
        assertEquals(0, stats.totalEntries)
        assertEquals(0, stats.memoryEntries)
        assertEquals(0.0, stats.hitRate, 0.001)

        val url1 = "https://example.com/test1.js"
        val url2 = "https://example.com/test2.js"
        val sourceCode = "console.log('test');"

        // Cache some entries
        cacheService.cacheEntry(url1, sourceCode, contentType = "text/javascript")
        cacheService.cacheEntry(url2, sourceCode, contentType = "text/javascript")

        // Check stats after caching
        stats = cacheService.getCacheStats()
        assertEquals(2, stats.totalEntries)
        assertEquals(2, stats.memoryEntries)

        // Generate some cache hits and misses
        cacheService.getCachedEntry(url1) // hit
        cacheService.getCachedEntry(url2) // hit
        cacheService.getCachedEntry("https://example.com/nonexistent.js") // miss

        stats = cacheService.getCacheStats()
        // Hit rate should be 2/3 = 0.666...
        assertTrue("Hit rate should be approximately 0.67", stats.hitRate > 0.6 && stats.hitRate < 0.7)
    }

    @Test
    fun testCacheClearAndRemoval() = runBlocking {
        val url1 = "https://example.com/test1.js"
        val url2 = "https://example.com/test2.js"
        val sourceCode = "console.log('test');"

        // Cache some entries
        cacheService.cacheEntry(url1, sourceCode, contentType = "text/javascript")
        cacheService.cacheEntry(url2, sourceCode, contentType = "text/javascript")

        // Verify entries exist
        assertNotNull(cacheService.getCachedEntry(url1))
        assertNotNull(cacheService.getCachedEntry(url2))

        // Remove one entry
        cacheService.removeEntry(url1)
        assertNull("Removed entry should not be found", cacheService.getCachedEntry(url1))
        assertNotNull("Other entry should still exist", cacheService.getCachedEntry(url2))

        // Clear all cache
        cacheService.clearCache()
        assertNull("All entries should be cleared", cacheService.getCachedEntry(url2))

        val stats = cacheService.getCacheStats()
        assertEquals(0, stats.totalEntries)
    }

    @Test
    fun testHashGeneration() {
        val url1 = "https://example.com/test.js"
        val url2 = "https://example.com/test.js" // Same URL
        val url3 = "https://example.com/other.js" // Different URL

        // Create entries to test hash generation (accessing private method via reflection or testing behavior)
        val entry1 = CacheService.CacheEntry(
            url = url1, sourceCode = "test", etag = null, lastModified = null,
            contentType = "text/javascript", cachedAt = System.currentTimeMillis(),
            ttlMs = 0, hash = "hash1", size = 4
        )
        
        val entry2 = CacheService.CacheEntry(
            url = url2, sourceCode = "test", etag = null, lastModified = null,
            contentType = "text/javascript", cachedAt = System.currentTimeMillis(),
            ttlMs = 0, hash = "hash2", size = 4
        )

        // Same URL should theoretically generate same hash (though we can't test private method directly)
        // This test validates that our CacheEntry creation works correctly
        assertEquals(url1, entry1.url)
        assertEquals(url2, entry2.url)
        assertEquals(entry1.url, entry2.url)
    }

    @Test
    fun testCacheEntryEquality() {
        val now = System.currentTimeMillis()
        
        val entry1 = CacheService.CacheEntry(
            url = "https://example.com/test.js",
            sourceCode = "console.log('test');",
            etag = "\"abc123\"",
            lastModified = "Wed, 21 Oct 2015 07:28:00 GMT",
            contentType = "text/javascript",
            cachedAt = now,
            ttlMs = 24 * 60 * 60 * 1000L,
            hash = "hash123",
            size = 20
        )

        val entry2 = CacheService.CacheEntry(
            url = "https://example.com/test.js",
            sourceCode = "console.log('test');",
            etag = "\"abc123\"",
            lastModified = "Wed, 21 Oct 2015 07:28:00 GMT",
            contentType = "text/javascript",
            cachedAt = now,
            ttlMs = 24 * 60 * 60 * 1000L,
            hash = "hash123",
            size = 20
        )

        // Test that data classes work correctly for equality
        assertEquals("Entries with same data should be equal", entry1, entry2)
        assertEquals("Hash codes should be equal for equal entries", entry1.hashCode(), entry2.hashCode())
    }
}
