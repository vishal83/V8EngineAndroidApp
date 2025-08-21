package com.visgupta.example.v8integrationandroidapp

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

/**
 * Comprehensive caching service for JavaScript files and compiled bytecode
 * Supports HTTP caching headers, bytecode caching, and intelligent cache management
 */
class CacheService(private val context: Context) {
    private val TAG = "CacheService"
    
    // Cache directories
    private val cacheDir = File(context.cacheDir, "js_cache")
    private val sourceDir = File(cacheDir, "source")
    private val bytecodeDir = File(cacheDir, "bytecode")
    private val metadataDir = File(cacheDir, "metadata")
    
    // In-memory cache for quick access
    private val memoryCache = ConcurrentHashMap<String, CacheEntry>()
    
    // Cache configuration
    private val maxCacheSize = 50 * 1024 * 1024 // 50MB total cache
    private val maxMemoryEntries = 100
    private val defaultTtlMs = 24 * 60 * 60 * 1000L // 24 hours
    
    data class CacheEntry(
        val url: String,
        val sourceCode: String,
        val bytecode: ByteArray? = null,
        val etag: String? = null,
        val lastModified: String? = null,
        val contentType: String? = null,
        val cachedAt: Long = System.currentTimeMillis(),
        val ttlMs: Long = 0,
        val hash: String,
        val size: Int
    ) {
        fun isExpired(): Boolean {
            return if (ttlMs > 0) {
                System.currentTimeMillis() - cachedAt > ttlMs
            } else {
                System.currentTimeMillis() - cachedAt > 24 * 60 * 60 * 1000L // 24h default
            }
        }
        
        fun needsRevalidation(): Boolean {
            // For now, only revalidate if expired - ignore header validation for debugging
            return isExpired()
        }
    }
    
    data class CacheStats(
        val totalEntries: Int,
        val totalSizeBytes: Long,
        val memoryEntries: Int,
        val diskEntries: Int,
        val hitRate: Double,
        val bytecodeEntries: Int
    )
    
    // Cache statistics
    private var cacheHits = 0L
    private var cacheMisses = 0L
    
    init {
        initializeCacheDirectories()
        loadMemoryCache()
        Log.i(TAG, "CacheService initialized with ${memoryCache.size} cached entries")
    }
    
    /**
     * Initialize cache directory structure
     */
    private fun initializeCacheDirectories() {
        listOf(cacheDir, sourceDir, bytecodeDir, metadataDir).forEach { dir ->
            if (!dir.exists()) {
                dir.mkdirs()
                Log.d(TAG, "Created cache directory: ${dir.absolutePath}")
            }
        }
    }
    
    /**
     * Load cache entries from disk into memory
     */
    private fun loadMemoryCache() {
        try {
            metadataDir.listFiles { _, name -> name.endsWith(".json") }?.forEach { metaFile ->
                try {
                    val metadata = JSONObject(metaFile.readText())
                    val hash = metaFile.nameWithoutExtension
                    
                    val sourceFile = File(sourceDir, "$hash.js")
                    val bytecodeFile = File(bytecodeDir, "$hash.qbc")
                    
                    if (sourceFile.exists()) {
                        val entry = CacheEntry(
                            url = metadata.getString("url"),
                            sourceCode = sourceFile.readText(),
                            bytecode = if (bytecodeFile.exists()) bytecodeFile.readBytes() else null,
                            etag = metadata.optString("etag").takeIf { it.isNotEmpty() },
                            lastModified = metadata.optString("lastModified").takeIf { it.isNotEmpty() },
                            contentType = metadata.optString("contentType").takeIf { it.isNotEmpty() },
                            cachedAt = metadata.getLong("cachedAt"),
                            ttlMs = metadata.optLong("ttlMs", defaultTtlMs),
                            hash = hash,
                            size = sourceFile.length().toInt()
                        )
                        
                        if (!entry.isExpired()) {
                            memoryCache[entry.url] = entry
                        } else {
                            // Clean up expired entries
                            cleanupEntry(hash)
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to load cache entry: ${metaFile.name}", e)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load memory cache", e)
        }
    }
    
    /**
     * Generate hash for URL to use as cache key
     */
    private fun generateHash(url: String): String {
        return MessageDigest.getInstance("SHA-256")
            .digest(url.toByteArray())
            .joinToString("") { "%02x".format(it) }
            .take(16) // Use first 16 chars for shorter filenames
    }
    
    /**
     * Get cached entry if available and valid
     */
    suspend fun getCachedEntry(url: String): CacheEntry? = withContext(Dispatchers.IO) {
        val entry = memoryCache[url]
        
        if (entry != null) {
            if (entry.isExpired()) {
                Log.d(TAG, "Cache entry expired for: $url")
                evictEntry(url)
                cacheMisses++
                Log.d(TAG, "Cache MISS for: $url")
                return@withContext null
            } else {
                cacheHits++
                Log.d(TAG, "Cache HIT for: $url")
                return@withContext entry
            }
        }
        
        cacheMisses++
        Log.d(TAG, "Cache MISS for: $url")
        return@withContext null
    }
    
    /**
     * Check if we should revalidate with server (for conditional requests)
     */
    fun shouldRevalidate(url: String): Pair<Boolean, CacheEntry?> {
        val entry = memoryCache[url]
        return if (entry?.needsRevalidation() == true) {
            true to entry
        } else {
            false to null
        }
    }
    
    /**
     * Store entry in cache
     */
    suspend fun cacheEntry(
        url: String,
        sourceCode: String,
        etag: String? = null,
        lastModified: String? = null,
        contentType: String? = null,
        ttlMs: Long = defaultTtlMs
    ): CacheEntry = withContext(Dispatchers.IO) {
        
        val hash = generateHash(url)
        val entry = CacheEntry(
            url = url,
            sourceCode = sourceCode,
            etag = etag,
            lastModified = lastModified,
            contentType = contentType,
            ttlMs = ttlMs,
            hash = hash,
            size = sourceCode.length
        )
        
        try {
            // Write source code to disk
            val sourceFile = File(sourceDir, "$hash.js")
            sourceFile.writeText(sourceCode)
            
            // Write metadata to disk
            val metadataFile = File(metadataDir, "$hash.json")
            val metadata = JSONObject().apply {
                put("url", url)
                put("cachedAt", entry.cachedAt)
                put("ttlMs", ttlMs)
                put("size", entry.size)
                etag?.let { put("etag", it) }
                lastModified?.let { put("lastModified", it) }
                contentType?.let { put("contentType", it) }
            }
            metadataFile.writeText(metadata.toString(2))
            
            // Add to memory cache
            memoryCache[url] = entry
            
            // Enforce cache size limits
            enforceCacheLimits()
            
            Log.i(TAG, "Cached entry for: $url (${entry.size} bytes)")
            return@withContext entry
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to cache entry for: $url", e)
            throw e
        }
    }
    
    /**
     * Cache compiled bytecode for faster execution
     */
    suspend fun cacheBytecode(url: String, bytecode: ByteArray): Boolean = withContext(Dispatchers.IO) {
        try {
            val entry = memoryCache[url] ?: return@withContext false
            val bytecodeFile = File(bytecodeDir, "${entry.hash}.qbc")
            
            bytecodeFile.writeBytes(bytecode)
            
            // Update memory cache entry with bytecode
            val updatedEntry = entry.copy(bytecode = bytecode)
            memoryCache[url] = updatedEntry
            
            Log.i(TAG, "Cached bytecode for: $url (${bytecode.size} bytes)")
            return@withContext true
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to cache bytecode for: $url", e)
            return@withContext false
        }
    }
    
    /**
     * Get cached bytecode if available
     */
    suspend fun getCachedBytecode(url: String): ByteArray? = withContext(Dispatchers.IO) {
        val entry = memoryCache[url]
        if (entry?.bytecode != null) {
            Log.d(TAG, "Found cached bytecode for: $url")
            return@withContext entry.bytecode
        }
        
        // Try loading from disk if not in memory
        if (entry != null) {
            val bytecodeFile = File(bytecodeDir, "${entry.hash}.qbc")
            if (bytecodeFile.exists()) {
                try {
                    val bytecode = bytecodeFile.readBytes()
                    // Update memory cache
                    memoryCache[url] = entry.copy(bytecode = bytecode)
                    Log.d(TAG, "Loaded bytecode from disk for: $url")
                    return@withContext bytecode
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to load bytecode from disk for: $url", e)
                }
            }
        }
        
        return@withContext null
    }
    
    /**
     * Evict entry from cache
     */
    private fun evictEntry(url: String) {
        val entry = memoryCache.remove(url)
        entry?.let { cleanupEntry(it.hash) }
    }
    
    /**
     * Clean up files for a cache entry
     */
    private fun cleanupEntry(hash: String) {
        try {
            File(sourceDir, "$hash.js").delete()
            File(bytecodeDir, "$hash.qbc").delete()
            File(metadataDir, "$hash.json").delete()
            Log.d(TAG, "Cleaned up cache entry: $hash")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to cleanup cache entry: $hash", e)
        }
    }
    
    /**
     * Enforce cache size and entry limits
     */
    private fun enforceCacheLimits() {
        // Enforce memory cache size
        while (memoryCache.size > maxMemoryEntries) {
            val oldestEntry = memoryCache.values.minByOrNull { it.cachedAt }
            oldestEntry?.let { evictEntry(it.url) }
        }
        
        // Enforce disk cache size
        val totalSize = calculateCacheSize()
        if (totalSize > maxCacheSize) {
            Log.i(TAG, "Cache size exceeded limit, cleaning up old entries")
            cleanupOldEntries(totalSize - maxCacheSize + (maxCacheSize * 0.1).toLong()) // Clean 10% extra
        }
    }
    
    /**
     * Calculate total cache size on disk
     */
    private fun calculateCacheSize(): Long {
        return listOf(sourceDir, bytecodeDir, metadataDir).sumOf { dir ->
            dir.listFiles()?.sumOf { it.length() } ?: 0L
        }
    }
    
    /**
     * Clean up old entries to free space
     */
    private fun cleanupOldEntries(bytesToFree: Long) {
        var freedBytes = 0L
        val sortedEntries = memoryCache.values.sortedBy { it.cachedAt }
        
        for (entry in sortedEntries) {
            if (freedBytes >= bytesToFree) break
            
            freedBytes += entry.size + (entry.bytecode?.size ?: 0)
            evictEntry(entry.url)
        }
        
        Log.i(TAG, "Freed $freedBytes bytes from cache")
    }
    
    /**
     * Clear all cache entries
     */
    suspend fun clearCache() = withContext(Dispatchers.IO) {
        try {
            memoryCache.clear()
            listOf(sourceDir, bytecodeDir, metadataDir).forEach { dir ->
                dir.listFiles()?.forEach { it.delete() }
            }
            cacheHits = 0
            cacheMisses = 0
            Log.i(TAG, "Cache cleared successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to clear cache", e)
        }
    }
    
    /**
     * Get cache statistics
     */
    fun getCacheStats(): CacheStats {
        val diskEntries = metadataDir.listFiles()?.size ?: 0
        val bytecodeEntries = bytecodeDir.listFiles()?.size ?: 0
        val totalRequests = cacheHits + cacheMisses
        val hitRate = if (totalRequests > 0) cacheHits.toDouble() / totalRequests else 0.0
        
        return CacheStats(
            totalEntries = memoryCache.size,
            totalSizeBytes = calculateCacheSize(),
            memoryEntries = memoryCache.size,
            diskEntries = diskEntries,
            hitRate = hitRate,
            bytecodeEntries = bytecodeEntries
        )
    }
    
    /**
     * Get all cached URLs
     */
    fun getCachedUrls(): List<String> {
        return memoryCache.keys.toList().sorted()
    }
    
    /**
     * Remove specific entry from cache
     */
    suspend fun removeEntry(url: String): Boolean = withContext(Dispatchers.IO) {
        return@withContext try {
            evictEntry(url)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to remove cache entry: $url", e)
            false
        }
    }
}
