#include <jni.h>
#include <string>
#include <vector>
#include <android/log.h>
#include <sstream>
#include <map>
#include <regex>
#include <algorithm>
#include <cmath>

#define LOG_TAG "QuickJSTest"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

// External byte transfer functions
extern "C" {
    bool bytetransfer_write_from_v8(const uint8_t* data, size_t length, const char* buffer_name = nullptr);
    bool bytetransfer_read_for_v8(uint8_t* dest, size_t length, size_t offset = 0, const char* buffer_name = nullptr);
    bool bytetransfer_get_info(size_t* size, size_t* capacity, const char* buffer_name = nullptr);
}

// Include real QuickJS headers
extern "C" {
#include "quickjs/quickjs.h"
#include "quickjs/quickjs-libc.h"
}

// Global JavaVM reference for JNI calls from native threads
JavaVM *g_jvm = nullptr;

// Global references for HTTP polyfills
static jobject g_quickjsBridgeInstance = nullptr;
static jmethodID g_handleHttpRequestMethod = nullptr;

// Forward declarations
static JSValue js_http_request(JSContext *ctx, JSValueConst this_val, int argc, JSValueConst *argv);
void initializeHttpPolyfill(JNIEnv *env, jobject bridgeInstance);
void addHttpPolyfills(JSContext *ctx);

// Native HTTP request function (called from JavaScript)
static JSValue js_http_request(JSContext *ctx, JSValueConst this_val, int argc, JSValueConst *argv) {
    if (argc < 1 || !g_quickjsBridgeInstance || !g_handleHttpRequestMethod) {
        return JS_ThrowReferenceError(ctx, "HTTP service not available");
    }
    
    // Get URL from first argument
    const char *url = JS_ToCString(ctx, argv[0]);
    if (!url) {
        return JS_ThrowTypeError(ctx, "URL must be a string");
    }
    
    // Get options from second argument (or empty object)
    const char *options = "{}";
    if (argc > 1) {
        options = JS_ToCString(ctx, argv[1]);
        if (!options) {
            JS_FreeCString(ctx, url);
            return JS_ThrowTypeError(ctx, "Options must be an object");
        }
    }
    
    // Get JNI environment
    JNIEnv *env = nullptr;
    
    // This is a simplified approach - in production you'd want proper JVM attachment
    if (!g_jvm || g_jvm->GetEnv((void**)&env, JNI_VERSION_1_6) != JNI_OK) {
        JS_FreeCString(ctx, url);
        if (argc > 1) JS_FreeCString(ctx, options);
        return JS_ThrowInternalError(ctx, "Failed to get JNI environment");
    }
    
    // Call Java method
    jstring jUrl = env->NewStringUTF(url);
    jstring jOptions = env->NewStringUTF(options);
    
    jstring jResult = (jstring)env->CallObjectMethod(g_quickjsBridgeInstance, 
        g_handleHttpRequestMethod, jUrl, jOptions);
    
    env->DeleteLocalRef(jUrl);
    env->DeleteLocalRef(jOptions);
    
    if (env->ExceptionCheck()) {
        env->ExceptionClear();
        JS_FreeCString(ctx, url);
        if (argc > 1) JS_FreeCString(ctx, options);
        return JS_ThrowInternalError(ctx, "HTTP request failed");
    }
    
    // Convert result back to JavaScript
    const char *resultStr = env->GetStringUTFChars(jResult, nullptr);
    JSValue result = JS_ParseJSON(ctx, resultStr, strlen(resultStr), "<http-response>");
    
    env->ReleaseStringUTFChars(jResult, resultStr);
    env->DeleteLocalRef(jResult);
    
    JS_FreeCString(ctx, url);
    if (argc > 1) JS_FreeCString(ctx, options);
    
    return result;
}

// Initialize HTTP polyfill references
void initializeHttpPolyfill(JNIEnv *env, jobject bridgeInstance) {
    if (g_quickjsBridgeInstance) {
        env->DeleteGlobalRef(g_quickjsBridgeInstance);
    }
    g_quickjsBridgeInstance = env->NewGlobalRef(bridgeInstance);
    
    jclass bridgeClass = env->GetObjectClass(bridgeInstance);
    g_handleHttpRequestMethod = env->GetMethodID(bridgeClass, "handleHttpRequest", 
        "(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;");
    
    if (!g_handleHttpRequestMethod) {
        LOGE("Failed to find handleHttpRequest method");
    }
}

// Add HTTP polyfills to QuickJS context
void addHttpPolyfills(JSContext *ctx) {
    // Add native HTTP request function
    JSValue global = JS_GetGlobalObject(ctx);
    JS_SetPropertyStr(ctx, global, "_nativeHttpRequest", 
        JS_NewCFunction(ctx, js_http_request, "_nativeHttpRequest", 2));
    
    // Add fetch polyfill
    const char *fetchPolyfill = R"(
(function() {
    // Fetch API polyfill
    globalThis.fetch = function(url, options) {
        options = options || {};
        
        return new Promise(function(resolve, reject) {
            try {
                var requestOptions = {
                    method: options.method || 'GET',
                    headers: options.headers || {},
                    body: options.body || null,
                    timeout: options.timeout || 30000,
                    redirect: options.redirect || 'follow',
                    credentials: options.credentials || 'same-origin'
                };
                
                var response = _nativeHttpRequest(url, JSON.stringify(requestOptions));
                
                if (response && response.status !== undefined) {
                    // Create Response object
                    var responseObj = {
                        status: response.status,
                        statusText: response.statusText,
                        ok: response.ok,
                        redirected: response.redirected,
                        url: response.url,
                        type: response.type,
                        headers: new Map(Object.entries(response.headers || {})),
                        
                        text: function() {
                            return Promise.resolve(response.body || '');
                        },
                        
                        json: function() {
                            return Promise.resolve(JSON.parse(response.body || '{}'));
                        },
                        
                        blob: function() {
                            return Promise.reject(new Error('Blob not supported'));
                        },
                        
                        arrayBuffer: function() {
                            return Promise.reject(new Error('ArrayBuffer not supported'));
                        }
                    };
                    
                    resolve(responseObj);
                } else {
                    reject(new Error('Network request failed'));
                }
            } catch (e) {
                reject(e);
            }
        });
    };
    
    // XMLHttpRequest polyfill
    globalThis.XMLHttpRequest = function() {
        this.readyState = 0;
        this.status = 0;
        this.statusText = '';
        this.responseText = '';
        this.responseXML = null;
        this.onreadystatechange = null;
        this._method = 'GET';
        this._url = '';
        this._headers = {};
        this._body = null;
        
        this.open = function(method, url, async) {
            this._method = method;
            this._url = url;
            this.readyState = 1;
            if (this.onreadystatechange) this.onreadystatechange();
        };
        
        this.setRequestHeader = function(header, value) {
            this._headers[header] = value;
        };
        
        this.send = function(body) {
            var self = this;
            this._body = body;
            this.readyState = 2;
            if (this.onreadystatechange) this.onreadystatechange();
            
            try {
                var options = {
                    method: this._method,
                    headers: this._headers,
                    body: this._body
                };
                
                var response = _nativeHttpRequest(this._url, JSON.stringify(options));
                
                this.status = response.status || 0;
                this.statusText = response.statusText || '';
                this.responseText = response.body || '';
                this.readyState = 4;
                
                if (this.onreadystatechange) this.onreadystatechange();
            } catch (e) {
                this.status = 0;
                this.statusText = 'Error';
                this.responseText = '';
                this.readyState = 4;
                if (this.onreadystatechange) this.onreadystatechange();
            }
        };
        
        this.abort = function() {
            this.readyState = 0;
        };
        
        this.getAllResponseHeaders = function() {
            return '';
        };
        
        this.getResponseHeader = function(header) {
            return null;
        };
    };
    
    // Constants
    globalThis.XMLHttpRequest.UNSENT = 0;
    globalThis.XMLHttpRequest.OPENED = 1;
    globalThis.XMLHttpRequest.HEADERS_RECEIVED = 2;
    globalThis.XMLHttpRequest.LOADING = 3;
    globalThis.XMLHttpRequest.DONE = 4;
})();
)";
    
    JSValue result = JS_Eval(ctx, fetchPolyfill, strlen(fetchPolyfill), "<fetch-polyfill>", JS_EVAL_TYPE_GLOBAL);
    if (JS_IsException(result)) {
        JSValue exception = JS_GetException(ctx);
        const char *exceptionStr = JS_ToCString(ctx, exception);
        LOGE("Failed to add HTTP polyfills: %s", exceptionStr ? exceptionStr : "Unknown error");
        if (exceptionStr) JS_FreeCString(ctx, exceptionStr);
        JS_FreeValue(ctx, exception);
    }
    JS_FreeValue(ctx, result);
    JS_FreeValue(ctx, global);
}

// Real QuickJS Engine implementation
class RealQuickJSEngine {
public:
    JSRuntime *runtime;  // Made public for memory stats access
private:
    JSContext *context;
    bool initialized;
    
public:
    RealQuickJSEngine() : runtime(nullptr), context(nullptr), initialized(false) {
    }
    
    bool initialize() {
        LOGI("Initializing Real QuickJS Engine");

        runtime = JS_NewRuntime();
        if (!runtime) {
            LOGE("Failed to create QuickJS runtime");
            return false;
        }

        // Set memory limits for mobile environment
        JS_SetMemoryLimit(runtime, 64 * 1024 * 1024); // 64MB limit
        JS_SetGCThreshold(runtime, 1024 * 1024);       // 1MB GC threshold

        context = JS_NewContext(runtime);
        if (!context) {
            LOGE("Failed to create QuickJS context");
            JS_FreeRuntime(runtime);
            runtime = nullptr;
            return false;
        }

        // Add standard library
        js_std_add_helpers(context, 0, nullptr);
        
        // Add HTTP polyfills (fetch and XMLHttpRequest)
        addHttpPolyfills(context);

        initialized = true;
        LOGI("QuickJS Engine initialized successfully with memory management and HTTP polyfills");
        return true;
    }
    
    std::string executeScript(const std::string& script) {
        if (!initialized || !context) {
            return "Error: QuickJS not initialized";
        }
        
        LOGI("Executing QuickJS script: %s", script.c_str());

        // Evaluate the JavaScript code
        JSValue result = JS_Eval(context, script.c_str(), script.length(),
                "<input>", JS_EVAL_TYPE_GLOBAL);

        if (JS_IsException(result)) {
            // Handle JavaScript exceptions
            JSValue exception = JS_GetException(context);
            const char *exceptionStr = JS_ToCString(context, exception);
            std::string error = "JavaScript Error: ";
            if (exceptionStr) {
                error += exceptionStr;
                JS_FreeCString(context, exceptionStr);
            } else {
                error += "Unknown error";
            }
            JS_FreeValue(context, exception);
            JS_FreeValue(context, result);
            LOGE("JavaScript execution error: %s", error.c_str());
            return error;
        }

        // Convert result to string
        const char *resultStr = JS_ToCString(context, result);
        std::string resultString;
        if (resultStr) {
            resultString = resultStr;
            JS_FreeCString(context, resultStr);
        } else {
            resultString = "undefined";
        }

        JS_FreeValue(context, result);

        LOGI("JavaScript result: %s", resultString.c_str());

        // Test byte transfer with result
        std::string fullResult = "QuickJS Result: " + resultString;
        if (bytetransfer_write_from_v8(reinterpret_cast<const uint8_t *>(fullResult.c_str()),
                fullResult.length(), "quickjs_output")) {
            LOGI("Successfully wrote %zu bytes to byte transfer system", fullResult.length());
        } else {
            LOGE("Failed to write to byte transfer system");
        }

        return resultString;
    }
    
    void cleanup() {
        LOGI("Cleaning up Real QuickJS Engine");

        if (context) {
            JS_FreeContext(context);
            context = nullptr;
        }

        if (runtime) {
            JS_FreeRuntime(runtime);
            runtime = nullptr;
        }

        initialized = false;
        LOGI("QuickJS cleanup complete");
    }
    
    bool isInitialized() const {
        return initialized && runtime && context;
    }
};

static RealQuickJSEngine *g_quickjsEngine = nullptr;

extern "C" {

// Initialize QuickJS Engine
JNIEXPORT jboolean JNICALL
Java_com_visgupta_example_v8integrationandroidapp_QuickJSBridge_initializeQuickJS(JNIEnv *env, jobject thiz) {
    LOGI("JNI: Initializing Real QuickJS Engine with HTTP polyfills");
    
    // Store JavaVM reference for HTTP requests
    if (!g_jvm) {
        env->GetJavaVM(&g_jvm);
    }
    
    if (g_quickjsEngine == nullptr) {
        g_quickjsEngine = new RealQuickJSEngine();
    }
    
    // Initialize HTTP polyfill references
    initializeHttpPolyfill(env, thiz);
    
    return g_quickjsEngine->initialize() ? JNI_TRUE : JNI_FALSE;
}

// Execute JavaScript code in QuickJS
JNIEXPORT jstring JNICALL
Java_com_visgupta_example_v8integrationandroidapp_QuickJSBridge_executeScript(JNIEnv *env, jobject thiz, jstring script) {
    if (g_quickjsEngine == nullptr) {
        return env->NewStringUTF("Error: QuickJS not initialized");
    }
    
    const char* scriptStr = env->GetStringUTFChars(script, nullptr);
    std::string result = g_quickjsEngine->executeScript(std::string(scriptStr));
    env->ReleaseStringUTFChars(script, scriptStr);
    
    return env->NewStringUTF(result.c_str());
}

// Cleanup QuickJS Engine
JNIEXPORT void JNICALL
Java_com_visgupta_example_v8integrationandroidapp_QuickJSBridge_cleanupQuickJS(JNIEnv *env, jobject thiz) {
LOGI("JNI: Cleaning up Real QuickJS Engine") ;
    
    if (g_quickjsEngine != nullptr) {
        g_quickjsEngine->cleanup();
        delete g_quickjsEngine;
        g_quickjsEngine = nullptr;
    }
}

// Check if QuickJS is initialized
JNIEXPORT jboolean JNICALL
Java_com_visgupta_example_v8integrationandroidapp_QuickJSBridge_isInitialized(JNIEnv *env, jobject thiz) {
    return (g_quickjsEngine != nullptr && g_quickjsEngine->isInitialized()) ? JNI_TRUE : JNI_FALSE;
}

// Test byte transfer integration with QuickJS
JNIEXPORT jboolean JNICALL
Java_com_visgupta_example_v8integrationandroidapp_QuickJSBridge_nativeTestByteTransfer(JNIEnv *env, jobject thiz, jbyteArray data, jstring bufferName) {
    if (!data) {
        LOGE("Input data is null");
        return JNI_FALSE;
    }
    
    jsize len = env->GetArrayLength(data);
    jbyte* bytes = env->GetByteArrayElements(data, nullptr);
    
    const char* buffer_name = nullptr;
    if (bufferName) {
        buffer_name = env->GetStringUTFChars(bufferName, nullptr);
    }
    
    bool success = bytetransfer_write_from_v8(reinterpret_cast<const uint8_t*>(bytes), len, buffer_name);
    
    if (bufferName) {
        env->ReleaseStringUTFChars(bufferName, buffer_name);
    }
    env->ReleaseByteArrayElements(data, bytes, JNI_ABORT);
    
    LOGI("QuickJS byte transfer write: %d bytes, success: %s", len, success ? "true" : "false");
    return success ? JNI_TRUE : JNI_FALSE;
}

// Read bytes from ByteTransfer to QuickJS
JNIEXPORT jbyteArray JNICALL
Java_com_visgupta_example_v8integrationandroidapp_QuickJSBridge_nativeReadBytesFromTransfer(JNIEnv *env, jobject thiz, jint length, jint offset, jstring bufferName) {
    if (length <= 0) {
        LOGE("Invalid length: %d", length);
        return nullptr;
    }
    
    const char* buffer_name = nullptr;
    if (bufferName) {
        buffer_name = env->GetStringUTFChars(bufferName, nullptr);
    }
    
    std::vector<uint8_t> buffer(length);
    bool success = bytetransfer_read_for_v8(buffer.data(), length, offset, buffer_name);
    
    if (bufferName) {
        env->ReleaseStringUTFChars(bufferName, buffer_name);
    }
    
    if (!success) {
        LOGE("Failed to read %d bytes from byte transfer system", length);
        return nullptr;
    }
    
    jbyteArray result = env->NewByteArray(length);
    env->SetByteArrayRegion(result, 0, length, reinterpret_cast<jbyte*>(buffer.data()));
    
    LOGI("QuickJS read from byte transfer: %d bytes", length);
    return result;
}

// Run QuickJS specific tests using real engine
JNIEXPORT jstring JNICALL
Java_com_visgupta_example_v8integrationandroidapp_QuickJSBridge_nativeRunQuickJSTests(JNIEnv *env, jobject thiz) {
LOGI("Running Real QuickJS comprehensive tests");
    
    if (g_quickjsEngine == nullptr || !g_quickjsEngine->isInitialized()) {
        return env->NewStringUTF("Error: QuickJS not initialized");
    }

std::string results = "Real QuickJS Comprehensive Tests:\n";
    bool allPassed = true;
    
    // Test 1: Arrow function
    std::string arrowTest = g_quickjsEngine->executeScript("const sum = (a, b) => a + b; sum(15, 27)");
    bool arrowPassed = arrowTest == "42";
    results += "1. Arrow Functions: " + std::string(arrowPassed ? "PASS" : "FAIL") + " (got: " + arrowTest + ")\n";
    allPassed &= arrowPassed;
    
    // Test 2: Destructuring
    std::string destructTest = g_quickjsEngine->executeScript("const [a, b] = [10, 20]; a + b");
    bool destructPassed = destructTest == "30";
    results += "2. Destructuring: " + std::string(destructPassed ? "PASS" : "FAIL") + " (got: " + destructTest + ")\n";
    allPassed &= destructPassed;
    
    // Test 3: Template literals
    std::string templateTest = g_quickjsEngine->executeScript("const name = 'QuickJS'; `Hello ${name}!`");
    bool templatePassed = templateTest == "Hello QuickJS!";
    results += "3. Template Literals: " + std::string(templatePassed ? "PASS" : "FAIL") + " (got: " + templateTest + ")\n";
    allPassed &= templatePassed;

// Test 4: Math operations
std::string mathTest = g_quickjsEngine->executeScript("Math.sqrt(16) + Math.pow(2, 3)");
bool mathPassed = mathTest == "12";
results += "4. Math Operations: " +
std::string(mathPassed
? "PASS" : "FAIL") + " (got: " + mathTest + ")\n";
allPassed &=
mathPassed;

// Test 5: Array methods
std::string arrayTest = g_quickjsEngine->executeScript("[1, 2, 3, 4].filter(x => x % 2 === 0).length");
bool arrayPassed = arrayTest == "2";
results += "5. Array Methods: " +
std::string(arrayPassed
? "PASS" : "FAIL") + " (got: " + arrayTest + ")\n";
allPassed &=
arrayPassed;

// Test 6: ByteTransfer integration
std::string testData = "Real QuickJS ByteTransfer Test";
    bool transferPassed = bytetransfer_write_from_v8(
        reinterpret_cast<const uint8_t*>(testData.c_str()), 
        testData.length(), 
        "quickjs_test"
    );
results += "6. ByteTransfer Integration: " +
std::string(transferPassed
? "PASS" : "FAIL") + "\n";
    allPassed &= transferPassed;

results += "\nReal QuickJS Engine Features:\n";
results += "- Full ES2023 specification support\n";
results += "- Real JavaScript execution (not mock)\n";
    results += "- Lightweight and fast startup\n";
results += "- Complete standard library\n";
    results += "- ByteTransfer integration\n";
    
    results += "\nOverall Result: " + std::string(allPassed ? "ALL TESTS PASSED" : "SOME TESTS FAILED");

LOGI("Real QuickJS tests completed: %s", allPassed ? "SUCCESS" : "FAILURE");
    return env->NewStringUTF(results.c_str());
}

// Get memory statistics
JNIEXPORT jstring JNICALL
Java_com_visgupta_example_v8integrationandroidapp_QuickJSBridge_nativeGetMemoryStats(JNIEnv *env, jobject thiz) {
    if (g_quickjsEngine == nullptr || !g_quickjsEngine->isInitialized()) {
        return env->NewStringUTF("QuickJS not initialized");
    }
    
    // Get memory statistics from QuickJS runtime
    JSMemoryUsage usage;
    JS_ComputeMemoryUsage(g_quickjsEngine->runtime, &usage);
    
    std::string stats = "QuickJS Memory Statistics:\n";
    stats += "Malloc size: " + std::to_string(usage.malloc_size) + " bytes\n";
    stats += "Malloc limit: " + std::to_string(usage.malloc_limit) + " bytes\n";
    stats += "Memory used: " + std::to_string(usage.memory_used_size) + " bytes\n";
    stats += "Objects: " + std::to_string(usage.obj_count) + "\n";
    stats += "Properties: " + std::to_string(usage.prop_count) + "\n";
    stats += "Shapes: " + std::to_string(usage.shape_count) + "\n";
    stats += "JS functions: " + std::to_string(usage.js_func_count) + "\n";
    stats += "C functions: " + std::to_string(usage.c_func_count) + "\n";
    stats += "Arrays: " + std::to_string(usage.array_count) + "\n";
    stats += "Fast arrays: " + std::to_string(usage.fast_array_count) + "\n";
    stats += "Binary objects: " + std::to_string(usage.binary_object_count) + " (" + 
             std::to_string(usage.binary_object_size) + " bytes)\n";
    
    double usage_percent = usage.malloc_limit > 0 ? 
        (double)usage.malloc_size / usage.malloc_limit * 100.0 : 0.0;
    stats += "Usage: " + std::to_string((int)usage_percent) + "%";
    
    return env->NewStringUTF(stats.c_str());
}

// HTTP request JNI function
JNIEXPORT jstring JNICALL
Java_com_visgupta_example_v8integrationandroidapp_QuickJSBridge_nativeHttpRequest(JNIEnv *env, jobject thiz, jstring url, jstring options) {
    // This function is not directly used but kept for compatibility
    // The actual HTTP requests go through js_http_request -> handleHttpRequest
    return env->NewStringUTF("{}");
}

}