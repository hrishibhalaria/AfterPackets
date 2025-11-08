#include <jni.h>
#include <string>
#include <vector>
#include "packet_parser.h"
#include <android/log.h>

#define LOG_TAG "NativeInterface"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)

static PacketParser* parser = nullptr;

extern "C" {

JNIEXPORT void JNICALL
Java_com_packethunter_mobile_capture_NativePacketParser_initParser(JNIEnv* env, jobject /* this */) {
    if (parser == nullptr) {
        parser = new PacketParser();
        LOGD("Native parser initialized");
    }
}

JNIEXPORT void JNICALL
Java_com_packethunter_mobile_capture_NativePacketParser_destroyParser(JNIEnv* env, jobject /* this */) {
    if (parser != nullptr) {
        delete parser;
        parser = nullptr;
        LOGD("Native parser destroyed");
    }
}

JNIEXPORT jobject JNICALL
Java_com_packethunter_mobile_capture_NativePacketParser_parsePacket(
    JNIEnv* env,
    jobject /* this */,
    jbyteArray data
) {
    if (parser == nullptr) {
        LOGD("Parser not initialized, initializing now");
        parser = new PacketParser();
    }
    
    jsize len = env->GetArrayLength(data);
    jbyte* bytes = env->GetByteArrayElements(data, nullptr);
    
    if (bytes == nullptr) {
        LOGD("Failed to get byte array");
        return nullptr;
    }
    
    // Parse the packet
    ParsedPacket result = parser->parsePacket(
        reinterpret_cast<const uint8_t*>(bytes),
        static_cast<size_t>(len)
    );
    
    env->ReleaseByteArrayElements(data, bytes, JNI_ABORT);
    
    // Find the ParsedPacketData class
    jclass packetClass = env->FindClass("com/packethunter/mobile/capture/ParsedPacketData");
    if (packetClass == nullptr) {
        LOGD("Could not find ParsedPacketData class");
        return nullptr;
    }
    
    // Get constructor
    jmethodID constructor = env->GetMethodID(packetClass, "<init>", "()V");
    if (constructor == nullptr) {
        LOGD("Could not find constructor");
        return nullptr;
    }
    
    // Create new instance
    jobject packetObj = env->NewObject(packetClass, constructor);
    if (packetObj == nullptr) {
        LOGD("Could not create object");
        return nullptr;
    }
    
    // Set fields
    jfieldID protocolField = env->GetFieldID(packetClass, "protocol", "Ljava/lang/String;");
    jfieldID sourceIpField = env->GetFieldID(packetClass, "sourceIp", "Ljava/lang/String;");
    jfieldID destIpField = env->GetFieldID(packetClass, "destIp", "Ljava/lang/String;");
    jfieldID sourcePortField = env->GetFieldID(packetClass, "sourcePort", "I");
    jfieldID destPortField = env->GetFieldID(packetClass, "destPort", "I");
    jfieldID lengthField = env->GetFieldID(packetClass, "length", "I");
    jfieldID flagsField = env->GetFieldID(packetClass, "flags", "Ljava/lang/String;");
    jfieldID payloadField = env->GetFieldID(packetClass, "payload", "[B");
    jfieldID payloadPreviewField = env->GetFieldID(packetClass, "payloadPreview", "Ljava/lang/String;");
    jfieldID httpMethodField = env->GetFieldID(packetClass, "httpMethod", "Ljava/lang/String;");
    jfieldID httpUrlField = env->GetFieldID(packetClass, "httpUrl", "Ljava/lang/String;");
    jfieldID dnsQueryField = env->GetFieldID(packetClass, "dnsQuery", "Ljava/lang/String;");
    jfieldID tlsSniField = env->GetFieldID(packetClass, "tlsSni", "Ljava/lang/String;");
    
    // Set string fields
    env->SetObjectField(packetObj, protocolField, env->NewStringUTF(result.protocol.c_str()));
    env->SetObjectField(packetObj, sourceIpField, env->NewStringUTF(result.source_ip.c_str()));
    env->SetObjectField(packetObj, destIpField, env->NewStringUTF(result.dest_ip.c_str()));
    env->SetIntField(packetObj, sourcePortField, result.source_port);
    env->SetIntField(packetObj, destPortField, result.dest_port);
    env->SetIntField(packetObj, lengthField, result.length);
    env->SetObjectField(packetObj, flagsField, env->NewStringUTF(result.flags.c_str()));
    env->SetObjectField(packetObj, payloadPreviewField, env->NewStringUTF(result.payload_preview.c_str()));
    
    // Set optional fields
    if (!result.http_method.empty()) {
        env->SetObjectField(packetObj, httpMethodField, env->NewStringUTF(result.http_method.c_str()));
    }
    if (!result.http_url.empty()) {
        env->SetObjectField(packetObj, httpUrlField, env->NewStringUTF(result.http_url.c_str()));
    }
    if (!result.dns_query.empty()) {
        env->SetObjectField(packetObj, dnsQueryField, env->NewStringUTF(result.dns_query.c_str()));
    }
    if (!result.tls_sni.empty()) {
        env->SetObjectField(packetObj, tlsSniField, env->NewStringUTF(result.tls_sni.c_str()));
    }
    
    // Set payload byte array
    if (!result.payload.empty()) {
        jbyteArray payloadArray = env->NewByteArray(result.payload.size());
        env->SetByteArrayRegion(payloadArray, 0, result.payload.size(), 
                               reinterpret_cast<const jbyte*>(result.payload.data()));
        env->SetObjectField(packetObj, payloadField, payloadArray);
    }
    
    return packetObj;
}

} // extern "C"
