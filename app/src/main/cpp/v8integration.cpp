#include <jni.h>
#include <string>
#include <vector>
#include <android/log.h>

// V8 includes (commented out until we have the actual V8 libraries)
// #include "v8.h"
// #include "libplatform/libplatform.h"

#define LOG_TAG "V8Test"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

// External byte transfer functions
extern "C" {
    bool bytetransfer_write_from_v8(const uint8_t* data, size_t length, const char* buffer_name = nullptr);
    bool bytetransfer_read_for_v8(uint8_t* dest, size_t length, size_t offset = 0, const char* buffer_name = nullptr);
    bool bytetransfer_get_info(size_t* size, size_t* capacity, const char* buffer_name = nullptr);
}

// Mock V8 functionality for initial testing
class MockV8Engine {
private:
    bool initialized;
    
public:
    MockV8Engine() : initialized(false) {}
    
    bool initialize() {
        LOGI("Initializing Mock V8 Engine");
        initialized = true;
        return true;
    }
    
    std::string executeScript(const std::string& script) {
        if (!initialized) {
            return "Error: V8 not initialized";
        }
        
        LOGI("Executing script: %s", script.c_str());
        
        // Mock execution with byte transfer
        std::string result = "V8 Result: " + script;
        
        // Test byte transfer - write result to byte transfer system
        if (bytetransfer_write_from_v8(reinterpret_cast<const uint8_t*>(result.c_str()), 
                                      result.length(), "v8_output")) {
            LOGI("Successfully wrote %zu bytes to byte transfer system", result.length());
        } else {
            LOGE("Failed to write to byte transfer system");
        }
        
        return result;
    }
    
    void cleanup() {
        LOGI("Cleaning up Mock V8 Engine");
        initialized = false;
    }
};

static MockV8Engine* g_v8Engine = nullptr;

extern "C" {

// Initialize V8 Engine
JNIEXPORT jboolean JNICALL
Java_com_visgupta_example_v8integrationandroidapp_V8Bridge_initializeV8(JNIEnv *env, jobject thiz) {
    LOGI("JNI: Initializing V8 Engine");
    
    if (g_v8Engine == nullptr) {
        g_v8Engine = new MockV8Engine();
    }
    
    return g_v8Engine->initialize() ? JNI_TRUE : JNI_FALSE;
}

// Execute JavaScript code
JNIEXPORT jstring JNICALL
Java_com_visgupta_example_v8integrationandroidapp_V8Bridge_executeScript(JNIEnv *env, jobject thiz, jstring script) {
    if (g_v8Engine == nullptr) {
        return env->NewStringUTF("Error: V8 not initialized");
    }
    
    const char* scriptStr = env->GetStringUTFChars(script, nullptr);
    std::string result = g_v8Engine->executeScript(std::string(scriptStr));
    env->ReleaseStringUTFChars(script, scriptStr);
    
    return env->NewStringUTF(result.c_str());
}

// Cleanup V8 Engine
JNIEXPORT void JNICALL
Java_com_visgupta_example_v8integrationandroidapp_V8Bridge_cleanupV8(JNIEnv *env, jobject thiz) {
    LOGI("JNI: Cleaning up V8 Engine");
    
    if (g_v8Engine != nullptr) {
        g_v8Engine->cleanup();
        delete g_v8Engine;
        g_v8Engine = nullptr;
    }
}

// Test function for basic data exchange
JNIEXPORT jstring JNICALL
Java_com_visgupta_example_v8integrationandroidapp_V8Bridge_testDataExchange(JNIEnv *env, jobject thiz, 
                                                                   jstring input, jint number, jboolean flag) {
    const char* inputStr = env->GetStringUTFChars(input, nullptr);
    
    std::string result = "Data Exchange Test:\n";
    result += "String: " + std::string(inputStr) + "\n";
    result += "Number: " + std::to_string(number) + "\n";
    result += "Boolean: " + std::string(flag ? "true" : "false");
    
    env->ReleaseStringUTFChars(input, inputStr);
    
    LOGI("Test data exchange result: %s", result.c_str());
    return env->NewStringUTF(result.c_str());
}

// Test byte transfer from V8 to ByteTransfer library
JNIEXPORT jboolean JNICALL
Java_com_visgupta_example_v8integrationandroidapp_V8Bridge_nativeTestByteTransfer(JNIEnv *env, jobject thiz, jbyteArray data, jstring bufferName) {
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
    
    LOGI("V8 byte transfer write: %d bytes, success: %s", len, success ? "true" : "false");
    return success ? JNI_TRUE : JNI_FALSE;
}

// Read bytes from ByteTransfer library to V8
JNIEXPORT jbyteArray JNICALL
Java_com_visgupta_example_v8integrationandroidapp_V8Bridge_nativeReadBytesFromTransfer(JNIEnv *env, jobject thiz, jint length, jint offset, jstring bufferName) {
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
    
    LOGI("V8 read from byte transfer: %d bytes", length);
    return result;
}

// Get byte transfer buffer info from V8 side
JNIEXPORT jstring JNICALL
Java_com_visgupta_example_v8integrationandroidapp_V8Bridge_nativeGetByteTransferInfo(JNIEnv *env, jobject thiz, jstring bufferName) {
    const char* buffer_name = nullptr;
    if (bufferName) {
        buffer_name = env->GetStringUTFChars(bufferName, nullptr);
    }
    
    size_t size, capacity;
    bool success = bytetransfer_get_info(&size, &capacity, buffer_name);
    
    if (bufferName) {
        env->ReleaseStringUTFChars(bufferName, buffer_name);
    }
    
    if (!success) {
        return env->NewStringUTF("Buffer not found or error occurred");
    }
    
    std::string info = "Buffer Info - Size: " + std::to_string(size) + 
                      ", Capacity: " + std::to_string(capacity) + 
                      ", Available: " + std::to_string(capacity - size);
    
    return env->NewStringUTF(info.c_str());
}

// Transfer bytes to buffer (wrapper for byte transfer system)
JNIEXPORT jboolean JNICALL
Java_com_visgupta_example_v8integrationandroidapp_V8Bridge_nativeTransferBytesToBuffer(JNIEnv *env, jobject thiz, jbyteArray data, jstring bufferName) {
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
    
    LOGI("Transfer to buffer write: %d bytes, success: %s", len, success ? "true" : "false");
    return success ? JNI_TRUE : JNI_FALSE;
}

// Read bytes from buffer (wrapper for byte transfer system)
JNIEXPORT jbyteArray JNICALL
Java_com_visgupta_example_v8integrationandroidapp_V8Bridge_nativeReadBytesFromBuffer(JNIEnv *env, jobject thiz, jint length, jint offset, jstring bufferName) {
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
        LOGE("Failed to read %d bytes from buffer system", length);
        return nullptr;
    }
    
    jbyteArray result = env->NewByteArray(length);
    env->SetByteArrayRegion(result, 0, length, reinterpret_cast<jbyte*>(buffer.data()));
    
    LOGI("Read from buffer: %d bytes", length);
    return result;
}

// Get buffer info from V8 perspective (wrapper for byte transfer system)
JNIEXPORT jstring JNICALL
Java_com_visgupta_example_v8integrationandroidapp_V8Bridge_nativeGetBufferInfoFromV8(JNIEnv *env, jobject thiz, jstring bufferName) {
    const char* buffer_name = nullptr;
    if (bufferName) {
        buffer_name = env->GetStringUTFChars(bufferName, nullptr);
    }
    
    size_t size, capacity;
    bool success = bytetransfer_get_info(&size, &capacity, buffer_name);
    
    if (bufferName) {
        env->ReleaseStringUTFChars(bufferName, buffer_name);
    }
    
    if (!success) {
        return env->NewStringUTF("V8 Buffer not found or error occurred");
    }
    
    std::string info = "V8 Buffer Info - Size: " + std::to_string(size) + 
                      ", Capacity: " + std::to_string(capacity) + 
                      ", Available: " + std::to_string(capacity - size);
    
    return env->NewStringUTF(info.c_str());
}

// Run comprehensive V8 ↔ ByteTransfer integration tests
JNIEXPORT jstring JNICALL
Java_com_visgupta_example_v8integrationandroidapp_V8Bridge_nativeRunV8ByteTransferTests(JNIEnv *env, jobject thiz) {
    LOGI("Running V8 ↔ ByteTransfer integration tests");
    
    std::string results = "V8 ↔ ByteTransfer Integration Tests:\n";
    bool allPassed = true;
    
    // First try to use shared buffer if named buffer doesn't exist
    const char* bufferName = nullptr; // Use shared buffer instead of "v8_test"
    
    // Test 1: Write test data
    std::string testData = "Hello from V8 integration test!";
    bool writeSuccess = bytetransfer_write_from_v8(
        reinterpret_cast<const uint8_t*>(testData.c_str()), 
        testData.length(), 
        bufferName
    );
    results += "1. Write Test: " + std::string(writeSuccess ? "PASS" : "FAIL") + "\n";
    if (!writeSuccess) {
        results += "   Error: Could not write to buffer. ByteTransfer system may not be initialized.\n";
    }
    allPassed &= writeSuccess;
    
    // Test 2: Read back the data
    std::vector<uint8_t> readBuffer(testData.length());
    bool readSuccess = bytetransfer_read_for_v8(
        readBuffer.data(), 
        testData.length(), 
        0, 
        bufferName
    );
    results += "2. Read Test: " + std::string(readSuccess ? "PASS" : "FAIL") + "\n";
    if (!readSuccess) {
        results += "   Error: Could not read from buffer.\n";
    }
    allPassed &= readSuccess;
    
    // Test 3: Verify data integrity
    bool dataMatch = readSuccess && 
        (memcmp(testData.c_str(), readBuffer.data(), testData.length()) == 0);
    results += "3. Data Integrity: " + std::string(dataMatch ? "PASS" : "FAIL") + "\n";
    if (readSuccess && !dataMatch) {
        results += "   Error: Data mismatch. Expected: '" + testData + "', Got: '" + 
                   std::string(reinterpret_cast<char*>(readBuffer.data()), testData.length()) + "'\n";
    }
    allPassed &= dataMatch;
    
    // Test 4: Buffer info
    size_t size, capacity;
    bool infoSuccess = bytetransfer_get_info(&size, &capacity, bufferName);
    results += "4. Buffer Info: " + std::string(infoSuccess ? "PASS" : "FAIL") + 
               " (Size: " + std::to_string(size) + ", Capacity: " + std::to_string(capacity) + ")\n";
    if (!infoSuccess) {
        results += "   Error: Could not get buffer information.\n";
    }
    allPassed &= infoSuccess;
    
    results += "\nOverall Result: " + std::string(allPassed ? "ALL TESTS PASSED" : "SOME TESTS FAILED");
    
    LOGI("V8 ByteTransfer tests completed: %s", allPassed ? "SUCCESS" : "FAILURE");
    return env->NewStringUTF(results.c_str());
}

}