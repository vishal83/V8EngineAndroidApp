package com.visgupta.example.v8integrationandroidapp

import kotlinx.coroutines.runBlocking
import okhttp3.*
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.junit.MockitoJUnitRunner
import org.mockito.Mockito.*
import java.io.IOException
import java.net.SocketTimeoutException
import java.util.concurrent.TimeUnit
import android.content.Context

@RunWith(MockitoJUnitRunner::class)
class HttpServiceTest {

    @Mock
    private lateinit var mockContext: Context

    @Mock
    private lateinit var mockCacheService: CacheService

    private lateinit var httpService: HttpService
    private lateinit var mockWebServer: MockWebServer

    @Before
    fun setUp() {
        httpService = HttpService()
        mockWebServer = MockWebServer()
        mockWebServer.start()
    }

    @After
    fun tearDown() {
        mockWebServer.shutdown()
    }

    @Test
    fun testSuccessfulGetRequest() = runBlocking {
        val responseBody = "console.log('Hello from server');"
        val mockResponse = MockResponse()
            .setResponseCode(200)
            .setHeader("Content-Type", "text/javascript")
            .setHeader("Content-Length", responseBody.length.toString())
            .setBody(responseBody)

        mockWebServer.enqueue(mockResponse)

        val url = mockWebServer.url("/test.js").toString()
        val options = HttpService.HttpRequestOptions(method = "GET")

        val response = httpService.executeRequest(url, options)

        assertEquals(200, response.status)
        assertEquals("OK", response.statusText)
        assertEquals(responseBody, response.body)
        assertEquals("text/javascript", response.headers["content-type"])
        assertEquals(url, response.url)

        val recordedRequest = mockWebServer.takeRequest()
        assertEquals("GET", recordedRequest.method)
        assertEquals("/test.js", recordedRequest.path)
    }

    @Test
    fun testPostRequestWithBody() = runBlocking {
        val requestBody = """{"message": "Hello Server"}"""
        val responseBody = """{"status": "received"}"""
        
        val mockResponse = MockResponse()
            .setResponseCode(201)
            .setHeader("Content-Type", "application/json")
            .setBody(responseBody)

        mockWebServer.enqueue(mockResponse)

        val url = mockWebServer.url("/api/messages").toString()
        val options = HttpService.HttpRequestOptions(
            method = "POST",
            headers = mapOf("Content-Type" to "application/json"),
            body = requestBody
        )

        val response = httpService.executeRequest(url, options)

        assertEquals(201, response.status)
        assertEquals("Created", response.statusText)
        assertEquals(responseBody, response.body)
        assertEquals("application/json", response.headers["content-type"])

        val recordedRequest = mockWebServer.takeRequest()
        assertEquals("POST", recordedRequest.method)
        assertEquals("/api/messages", recordedRequest.path)
        assertEquals(requestBody, recordedRequest.body.readUtf8())
        assertEquals("application/json", recordedRequest.getHeader("Content-Type"))
    }

    @Test
    fun testCustomHeaders() = runBlocking {
        val mockResponse = MockResponse()
            .setResponseCode(200)
            .setBody("OK")

        mockWebServer.enqueue(mockResponse)

        val url = mockWebServer.url("/test").toString()
        val customHeaders = mapOf(
            "Authorization" to "Bearer token123",
            "X-Custom-Header" to "custom-value",
            "User-Agent" to "V8EngineAndroidApp/1.0"
        )
        val options = HttpService.HttpRequestOptions(
            method = "GET",
            headers = customHeaders
        )

        httpService.executeRequest(url, options)

        val recordedRequest = mockWebServer.takeRequest()
        assertEquals("Bearer token123", recordedRequest.getHeader("Authorization"))
        assertEquals("custom-value", recordedRequest.getHeader("X-Custom-Header"))
        assertEquals("V8EngineAndroidApp/1.0", recordedRequest.getHeader("User-Agent"))
    }

    @Test
    fun testHttpErrorHandling() = runBlocking {
        val errorResponse = MockResponse()
            .setResponseCode(404)
            .setBody("Not Found")

        mockWebServer.enqueue(errorResponse)

        val url = mockWebServer.url("/nonexistent.js").toString()
        val options = HttpService.HttpRequestOptions(method = "GET")

        val response = httpService.executeRequest(url, options)

        assertEquals(404, response.status)
        assertEquals("Client Error", response.statusText)
        assertEquals("Not Found", response.body)
    }

    @Test
    fun testServerErrorHandling() = runBlocking {
        val errorResponse = MockResponse()
            .setResponseCode(500)
            .setBody("Internal Server Error")

        mockWebServer.enqueue(errorResponse)

        val url = mockWebServer.url("/error").toString()
        val options = HttpService.HttpRequestOptions(method = "GET")

        val response = httpService.executeRequest(url, options)

        assertEquals(500, response.status)
        assertEquals("Server Error", response.statusText)
        assertEquals("Internal Server Error", response.body)
    }

    @Test
    fun testTimeoutHandling() = runBlocking {
        // Set a very short timeout for the test
        val mockResponse = MockResponse()
            .setBodyDelay(5, TimeUnit.SECONDS) // Delay longer than timeout
            .setBody("This should timeout")

        mockWebServer.enqueue(mockResponse)

        val url = mockWebServer.url("/slow").toString()
        val options = HttpService.HttpRequestOptions(method = "GET")

        val response = httpService.executeRequest(url, options)

        // Should return error response due to timeout
        assertEquals(0, response.status)
        assertTrue("Response should contain timeout error", 
                  response.body.contains("timeout") || response.body.contains("failed"))
    }

    @Test
    fun testConditionalRequestWithETag() = runBlocking {
        val etag = "\"abc123\""
        val cachedContent = "console.log('cached');"
        
        // Mock cache entry
        val cacheEntry = CacheService.CacheEntry(
            url = "test-url",
            sourceCode = cachedContent,
            etag = etag,
            lastModified = null,
            contentType = "text/javascript",
            cachedAt = System.currentTimeMillis(),
            ttlMs = 24 * 60 * 60 * 1000L,
            hash = "hash123",
            size = cachedContent.length
        )

        // Server returns 304 Not Modified
        val mockResponse = MockResponse()
            .setResponseCode(304)
            .setHeader("ETag", etag)

        mockWebServer.enqueue(mockResponse)

        val url = mockWebServer.url("/test.js").toString()
        val options = HttpService.HttpRequestOptions(method = "GET")

        `when`(mockCacheService.getCachedEntry(url)).thenReturn(cacheEntry)

        val response = httpService.executeRequest(url, options, mockCacheService)

        assertEquals(200, response.status) // Should return 200 with cached content
        assertEquals(cachedContent, response.body)
        assertEquals("REVALIDATED", response.headers["x-cache"])

        val recordedRequest = mockWebServer.takeRequest()
        assertEquals(etag, recordedRequest.getHeader("If-None-Match"))
    }

    @Test
    fun testConditionalRequestWithLastModified() = runBlocking {
        val lastModified = "Wed, 21 Oct 2015 07:28:00 GMT"
        val cachedContent = "console.log('cached with last-modified');"
        
        // Mock cache entry
        val cacheEntry = CacheService.CacheEntry(
            url = "test-url",
            sourceCode = cachedContent,
            etag = null,
            lastModified = lastModified,
            contentType = "text/javascript",
            cachedAt = System.currentTimeMillis(),
            ttlMs = 24 * 60 * 60 * 1000L,
            hash = "hash123",
            size = cachedContent.length
        )

        // Server returns 304 Not Modified
        val mockResponse = MockResponse()
            .setResponseCode(304)
            .setHeader("Last-Modified", lastModified)

        mockWebServer.enqueue(mockResponse)

        val url = mockWebServer.url("/test.js").toString()
        val options = HttpService.HttpRequestOptions(method = "GET")

        `when`(mockCacheService.getCachedEntry(url)).thenReturn(cacheEntry)
        `when`(mockCacheService.shouldRevalidate(url)).thenReturn(Pair(true, cacheEntry))

        val response = httpService.executeRequest(url, options, mockCacheService)

        assertEquals(200, response.status)
        assertEquals(cachedContent, response.body)

        val recordedRequest = mockWebServer.takeRequest()
        assertEquals(lastModified, recordedRequest.getHeader("If-Modified-Since"))
    }

    @Test
    fun testCacheIntegration() = runBlocking {
        val responseBody = "console.log('Fresh content');"
        val etag = "\"new-etag\""
        val lastModified = "Thu, 22 Oct 2015 08:30:00 GMT"
        
        val mockResponse = MockResponse()
            .setResponseCode(200)
            .setHeader("Content-Type", "text/javascript")
            .setHeader("ETag", etag)
            .setHeader("Last-Modified", lastModified)
            .setBody(responseBody)

        mockWebServer.enqueue(mockResponse)

        val url = mockWebServer.url("/fresh.js").toString()
        val options = HttpService.HttpRequestOptions(method = "GET")

        val response = httpService.executeRequest(url, options, mockCacheService)

        assertEquals(200, response.status)
        assertEquals(responseBody, response.body)
        assertEquals("MISS", response.headers["x-cache"])

        // Verify cache was called to store the response
        verify(mockCacheService).cacheEntry(
            url = eq(url),
            sourceCode = eq(responseBody),
            etag = eq(etag),
            lastModified = eq(lastModified),
            contentType = eq("text/javascript")
        )
    }

    @Test
    fun testRequestOptionsJsonParsing() {
        val jsonString = """{
            "method": "POST",
            "headers": {
                "Content-Type": "application/json",
                "Authorization": "Bearer token123"
            },
            "body": "{\"key\": \"value\"}"
        }"""

        val options = httpService.parseRequestOptions(jsonString)

        assertEquals("POST", options.method)
        assertEquals("application/json", options.headers["Content-Type"])
        assertEquals("Bearer token123", options.headers["Authorization"])
        assertEquals("{\"key\": \"value\"}", options.body)
    }

    @Test
    fun testResponseToJson() {
        val response = HttpService.HttpResponse(
            status = 200,
            statusText = "OK",
            headers = mapOf(
                "content-type" to "text/javascript",
                "content-length" to "25",
                "x-cache" to "HIT"
            ),
            body = "console.log('test');",
            url = "https://example.com/test.js"
        )

        val jsonString = httpService.responseToJson(response)
        
        assertTrue("JSON should contain status", jsonString.contains("\"status\":200"))
        assertTrue("JSON should contain statusText", jsonString.contains("\"statusText\":\"OK\""))
        assertTrue("JSON should contain body", jsonString.contains("console.log('test')"))
        assertTrue("JSON should contain headers", jsonString.contains("\"content-type\":\"text/javascript\""))
        assertTrue("JSON should contain url", jsonString.contains("\"url\":\"https://example.com/test.js\""))
    }

    @Test
    fun testInvalidJsonHandling() {
        val invalidJson = "{ invalid json structure"
        
        val options = httpService.parseRequestOptions(invalidJson)
        
        // Should return default options on parse error
        assertEquals("GET", options.method)
        assertTrue("Headers should be empty", options.headers.isEmpty())
        assertNull("Body should be null", options.body)
    }

    @Test
    fun testEmptyResponseHandling() = runBlocking {
        val mockResponse = MockResponse()
            .setResponseCode(204) // No Content
            .setBody("")

        mockWebServer.enqueue(mockResponse)

        val url = mockWebServer.url("/empty").toString()
        val options = HttpService.HttpRequestOptions(method = "GET")

        val response = httpService.executeRequest(url, options)

        assertEquals(204, response.status)
        assertEquals("", response.body)
    }

    @Test
    fun testLargeResponseHandling() = runBlocking {
        val largeContent = "console.log('large');".repeat(10000) // ~200KB
        
        val mockResponse = MockResponse()
            .setResponseCode(200)
            .setHeader("Content-Type", "text/javascript")
            .setBody(largeContent)

        mockWebServer.enqueue(mockResponse)

        val url = mockWebServer.url("/large.js").toString()
        val options = HttpService.HttpRequestOptions(method = "GET")

        val response = httpService.executeRequest(url, options)

        assertEquals(200, response.status)
        assertEquals(largeContent, response.body)
        assertEquals(largeContent.length, response.body.length)
    }
}
