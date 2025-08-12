#include <jni.h>
#include <string>
#include <vector>
#include <memory>
#include <cstring>
#include <map>
#include <android/log.h>

#define LOG_TAG "ByteTransfer"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

// Shared memory structure for byte transfer
struct ByteBuffer {
    uint8_t* data;
    size_t size;
    size_t capacity;
    bool is_owner;
    
    ByteBuffer(size_t cap) : size(0), capacity(cap), is_owner(true) {
        data = new uint8_t[capacity];
        memset(data, 0, capacity);
    }
    
    ByteBuffer(uint8_t* external_data, size_t data_size) 
        : data(external_data), size(data_size), capacity(data_size), is_owner(false) {
    }
    
    ~ByteBuffer() {
        if (is_owner && data) {
            delete[] data;
        }
    }
    
    bool write(const uint8_t* src, size_t len) {
        if (size + len > capacity) {
            LOGE("Buffer overflow: trying to write %zu bytes, available: %zu", len, capacity - size);
            return false;
        }
        memcpy(data + size, src, len);
        size += len;
        return true;
    }
    
    bool read(uint8_t* dest, size_t len, size_t offset = 0) const {
        if (offset + len > size) {
            LOGE("Buffer underflow: trying to read %zu bytes at offset %zu, available: %zu", len, offset, size);
            return false;
        }
        memcpy(dest, data + offset, len);
        return true;
    }
    
    void clear() {
        size = 0;
        memset(data, 0, capacity);
    }
};

// Global buffer for inter-library communication
static std::unique_ptr<ByteBuffer> g_sharedBuffer = nullptr;
static std::vector<std::unique_ptr<ByteBuffer>> g_bufferPool;

// Buffer registry for named buffers
static std::map<std::string, std::unique_ptr<ByteBuffer>> g_namedBuffers;

extern "C" {

// Initialize byte transfer system
JNIEXPORT jboolean JNICALL
Java_com_visgupta_example_v8integrationandroidapp_ByteTransferBridge_nativeInitializeByteTransfer(JNIEnv *env, jobject thiz, jint bufferSize) {
    LOGI("Initializing byte transfer system with buffer size: %d", bufferSize);
    
    try {
        g_sharedBuffer = std::make_unique<ByteBuffer>(bufferSize);
        LOGI("Byte transfer system initialized successfully");
        return JNI_TRUE;
    } catch (const std::exception& e) {
        LOGE("Failed to initialize byte transfer system: %s", e.what());
        return JNI_FALSE;
    }
}

// Create a named buffer
JNIEXPORT jboolean JNICALL
Java_com_visgupta_example_v8integrationandroidapp_ByteTransferBridge_nativeCreateNamedBuffer(JNIEnv *env, jobject thiz, jstring name, jint size) {
    const char* bufferName = env->GetStringUTFChars(name, nullptr);
    
    try {
        std::string key(bufferName);
        g_namedBuffers[key] = std::make_unique<ByteBuffer>(size);
        LOGI("Created named buffer '%s' with size %d", bufferName, size);
        env->ReleaseStringUTFChars(name, bufferName);
        return JNI_TRUE;
    } catch (const std::exception& e) {
        LOGE("Failed to create named buffer '%s': %s", bufferName, e.what());
        env->ReleaseStringUTFChars(name, bufferName);
        return JNI_FALSE;
    }
}

// Write bytes to shared buffer
JNIEXPORT jboolean JNICALL
Java_com_visgupta_example_v8integrationandroidapp_ByteTransferBridge_nativeWriteBytes(JNIEnv *env, jobject thiz, jbyteArray data) {
    if (!g_sharedBuffer) {
        LOGE("Byte transfer system not initialized");
        return JNI_FALSE;
    }
    
    jsize len = env->GetArrayLength(data);
    jbyte* bytes = env->GetByteArrayElements(data, nullptr);
    
    bool success = g_sharedBuffer->write(reinterpret_cast<uint8_t*>(bytes), len);
    
    env->ReleaseByteArrayElements(data, bytes, JNI_ABORT);
    
    LOGI("Wrote %d bytes to shared buffer, success: %s", len, success ? "true" : "false");
    return success ? JNI_TRUE : JNI_FALSE;
}

// Write bytes to named buffer
JNIEXPORT jboolean JNICALL
Java_com_visgupta_example_v8integrationandroidapp_ByteTransferBridge_nativeWriteBytesToNamed(JNIEnv *env, jobject thiz, jstring name, jbyteArray data) {
    const char* bufferName = env->GetStringUTFChars(name, nullptr);
    std::string key(bufferName);
    env->ReleaseStringUTFChars(name, bufferName);
    
    auto it = g_namedBuffers.find(key);
    if (it == g_namedBuffers.end()) {
        LOGE("Named buffer '%s' not found", key.c_str());
        return JNI_FALSE;
    }
    
    jsize len = env->GetArrayLength(data);
    jbyte* bytes = env->GetByteArrayElements(data, nullptr);
    
    bool success = it->second->write(reinterpret_cast<uint8_t*>(bytes), len);
    
    env->ReleaseByteArrayElements(data, bytes, JNI_ABORT);
    
    LOGI("Wrote %d bytes to named buffer '%s', success: %s", len, key.c_str(), success ? "true" : "false");
    return success ? JNI_TRUE : JNI_FALSE;
}

// Read bytes from shared buffer
JNIEXPORT jbyteArray JNICALL
Java_com_visgupta_example_v8integrationandroidapp_ByteTransferBridge_nativeReadBytes(JNIEnv *env, jobject thiz, jint length, jint offset) {
    if (!g_sharedBuffer) {
        LOGE("Byte transfer system not initialized");
        return nullptr;
    }
    
    if (length <= 0 || offset < 0) {
        LOGE("Invalid parameters: length=%d, offset=%d", length, offset);
        return nullptr;
    }
    
    jbyteArray result = env->NewByteArray(length);
    if (!result) {
        LOGE("Failed to allocate byte array of size %d", length);
        return nullptr;
    }
    
    std::vector<uint8_t> buffer(length);
    if (!g_sharedBuffer->read(buffer.data(), length, offset)) {
        LOGE("Failed to read %d bytes from offset %d", length, offset);
        return nullptr;
    }
    
    env->SetByteArrayRegion(result, 0, length, reinterpret_cast<jbyte*>(buffer.data()));
    
    LOGI("Read %d bytes from shared buffer at offset %d", length, offset);
    return result;
}

// Read bytes from named buffer
JNIEXPORT jbyteArray JNICALL
Java_com_visgupta_example_v8integrationandroidapp_ByteTransferBridge_nativeReadBytesFromNamed(JNIEnv *env, jobject thiz, jstring name, jint length, jint offset) {
    const char* bufferName = env->GetStringUTFChars(name, nullptr);
    std::string key(bufferName);
    env->ReleaseStringUTFChars(name, bufferName);
    
    auto it = g_namedBuffers.find(key);
    if (it == g_namedBuffers.end()) {
        LOGE("Named buffer '%s' not found", key.c_str());
        return nullptr;
    }
    
    if (length <= 0 || offset < 0) {
        LOGE("Invalid parameters: length=%d, offset=%d", length, offset);
        return nullptr;
    }
    
    jbyteArray result = env->NewByteArray(length);
    if (!result) {
        LOGE("Failed to allocate byte array of size %d", length);
        return nullptr;
    }
    
    std::vector<uint8_t> buffer(length);
    if (!it->second->read(buffer.data(), length, offset)) {
        LOGE("Failed to read %d bytes from named buffer '%s' at offset %d", length, key.c_str(), offset);
        return nullptr;
    }
    
    env->SetByteArrayRegion(result, 0, length, reinterpret_cast<jbyte*>(buffer.data()));
    
    LOGI("Read %d bytes from named buffer '%s' at offset %d", length, key.c_str(), offset);
    return result;
}

// Clear buffer
JNIEXPORT void JNICALL
Java_com_visgupta_example_v8integrationandroidapp_ByteTransferBridge_nativeClearBuffer(JNIEnv *env, jobject thiz, jstring name) {
    if (name == nullptr) {
        if (g_sharedBuffer) {
            g_sharedBuffer->clear();
            LOGI("Cleared shared buffer");
        }
    } else {
        const char* bufferName = env->GetStringUTFChars(name, nullptr);
        std::string key(bufferName);
        env->ReleaseStringUTFChars(name, bufferName);
        
        auto it = g_namedBuffers.find(key);
        if (it != g_namedBuffers.end()) {
            it->second->clear();
            LOGI("Cleared named buffer '%s'", key.c_str());
        }
    }
}

// Cleanup byte transfer system
JNIEXPORT void JNICALL
Java_com_visgupta_example_v8integrationandroidapp_ByteTransferBridge_nativeCleanup(JNIEnv *env, jobject thiz) {
    LOGI("Cleaning up byte transfer system");
    
    g_sharedBuffer.reset();
    g_bufferPool.clear();
    g_namedBuffers.clear();
    
    LOGI("Byte transfer system cleanup complete");
}

// Native-to-native interface for V8 library
extern "C" {
    // Function that V8 library can call to write bytes
    bool bytetransfer_write_from_v8(const uint8_t* data, size_t length, const char* buffer_name = nullptr) {
        if (buffer_name) {
            std::string key(buffer_name);
            auto it = g_namedBuffers.find(key);
            if (it != g_namedBuffers.end()) {
                return it->second->write(data, length);
            }
            return false;
        } else if (g_sharedBuffer) {
            return g_sharedBuffer->write(data, length);
        }
        return false;
    }
    
    // Function that V8 library can call to read bytes
    bool bytetransfer_read_for_v8(uint8_t* dest, size_t length, size_t offset = 0, const char* buffer_name = nullptr) {
        if (buffer_name) {
            std::string key(buffer_name);
            auto it = g_namedBuffers.find(key);
            if (it != g_namedBuffers.end()) {
                return it->second->read(dest, length, offset);
            }
            return false;
        } else if (g_sharedBuffer) {
            return g_sharedBuffer->read(dest, length, offset);
        }
        return false;
    }
    
    // Get buffer info for V8 library
    bool bytetransfer_get_info(size_t* size, size_t* capacity, const char* buffer_name = nullptr) {
        ByteBuffer* buffer = nullptr;
        
        if (buffer_name) {
            std::string key(buffer_name);
            auto it = g_namedBuffers.find(key);
            if (it != g_namedBuffers.end()) {
                buffer = it->second.get();
            }
        } else {
            buffer = g_sharedBuffer.get();
        }
        
        if (buffer) {
            *size = buffer->size;
            *capacity = buffer->capacity;
            return true;
        }
        return false;
    }
}

}