/**
 * native-lib.cpp  —  v3
 *
 * Changes from v2:
 * • verifyDexIntegrity() JNI bridge REMOVED.
 *   On non-rooted Android, APK Signature Scheme v2/v3 is enforced by the OS
 *   at install time and re-verified on every classloader attach.  A redundant
 *   C++ SHA-256 check over classes.dex burns CPU/battery on every cold start
 *   without providing any additional security guarantee that the OS does not
 *   already provide.  anti_tamper.cpp is also removed from the build.
 *
 * • #include "include/anti_tamper.h" removed.
 *
 * All other surfaces (registerVpnService, startProxyEngine, stopProxyEngine,
 * the three SNI/ECH callbacks) are unchanged.
 */

#include <jni.h>
#include <android/log.h>

#include <cstring>
#include <string>

#include "include/sni_parser.h"

#define LOG_TAG "PCNative"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO,  LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

// ---------------------------------------------------------------------------
// Module-level JVM reference
// ---------------------------------------------------------------------------

static JavaVM* g_jvm = nullptr;

JNIEXPORT jint JNI_OnLoad(JavaVM* vm, void* /*reserved*/) {
    g_jvm = vm;
    LOGI("JNI_OnLoad: parental_control_native v3 loaded");
    return JNI_VERSION_1_6;
}

JNIEXPORT void JNI_OnUnload(JavaVM* /*vm*/, void* /*reserved*/) {
    proxy_engine_stop();
    LOGI("JNI_OnUnload");
}

// ---------------------------------------------------------------------------
// VPN Service registration — must be called before startProxyEngine()
// ---------------------------------------------------------------------------

extern "C"
JNIEXPORT void JNICALL
Java_com_example_parentalcontrol_NativeBridge_registerVpnService(
        JNIEnv* env,
        jobject /* thiz */,
        jobject service) {

    if (!service) {
        LOGE("registerVpnService: null service reference");
        return;
    }

    jobject global_ref = env->NewGlobalRef(service);
    protect_bridge_init(g_jvm, global_ref);
    LOGI("registerVpnService: protect() bridge initialised");
}

// ---------------------------------------------------------------------------
// Proxy engine start / stop
// ---------------------------------------------------------------------------

static jobject g_bridge_global = nullptr;

extern "C"
JNIEXPORT void JNICALL
Java_com_example_parentalcontrol_NativeBridge_startProxyEngine(
        JNIEnv* env,
        jobject thiz,
        jint    vpnFd) {

    if (g_bridge_global) {
        env->DeleteGlobalRef(g_bridge_global);
    }
    g_bridge_global = env->NewGlobalRef(thiz);

    ProxyEngineConfig cfg{};
    cfg.tun_fd     = vpnFd;
    cfg.jvm        = g_jvm;
    cfg.bridge_obj = g_bridge_global;

    cfg.on_sni_allowed = [](const std::string& host) {
        JNIEnv* e = nullptr; bool att = false;
        if (g_jvm->GetEnv(reinterpret_cast<void**>(&e), JNI_VERSION_1_6)
                == JNI_EDETACHED) {
            g_jvm->AttachCurrentThread(&e, nullptr); att = true;
        }
        if (e && g_bridge_global) {
            jclass    cls = e->GetObjectClass(g_bridge_global);
            jmethodID mid = e->GetMethodID(cls, "onSniAllowed", "(Ljava/lang/String;)V");
            if (mid) {
                jstring jh = e->NewStringUTF(host.c_str());
                e->CallVoidMethod(g_bridge_global, mid, jh);
                e->DeleteLocalRef(jh);
            }
            e->DeleteLocalRef(cls);
        }
        if (att) g_jvm->DetachCurrentThread();
    };

    cfg.on_sni_blocked = [](const std::string& host) {
        JNIEnv* e = nullptr; bool att = false;
        if (g_jvm->GetEnv(reinterpret_cast<void**>(&e), JNI_VERSION_1_6)
                == JNI_EDETACHED) {
            g_jvm->AttachCurrentThread(&e, nullptr); att = true;
        }
        if (e && g_bridge_global) {
            jclass    cls = e->GetObjectClass(g_bridge_global);
            jmethodID mid = e->GetMethodID(cls, "onSniBlocked", "(Ljava/lang/String;)V");
            if (mid) {
                jstring jh = e->NewStringUTF(host.c_str());
                e->CallVoidMethod(g_bridge_global, mid, jh);
                e->DeleteLocalRef(jh);
            }
            e->DeleteLocalRef(cls);
        }
        if (att) g_jvm->DetachCurrentThread();
    };

    cfg.on_ech = [](const std::string& ip) {
        JNIEnv* e = nullptr; bool att = false;
        if (g_jvm->GetEnv(reinterpret_cast<void**>(&e), JNI_VERSION_1_6)
                == JNI_EDETACHED) {
            g_jvm->AttachCurrentThread(&e, nullptr); att = true;
        }
        if (e && g_bridge_global) {
            jclass    cls = e->GetObjectClass(g_bridge_global);
            jmethodID mid = e->GetMethodID(cls, "onEchConnection", "(Ljava/lang/String;)V");
            if (mid) {
                jstring ji = e->NewStringUTF(ip.c_str());
                e->CallVoidMethod(g_bridge_global, mid, ji);
                e->DeleteLocalRef(ji);
            }
            e->DeleteLocalRef(cls);
        }
        if (att) g_jvm->DetachCurrentThread();
    };

    proxy_engine_start(cfg);
    LOGI("startProxyEngine: engine started (tun_fd=%d)", vpnFd);
}

extern "C"
JNIEXPORT void JNICALL
Java_com_example_parentalcontrol_NativeBridge_stopProxyEngine(
        JNIEnv* env,
        jobject /* thiz */) {
    proxy_engine_stop();

    if (g_bridge_global) {
        env->DeleteGlobalRef(g_bridge_global);
        g_bridge_global = nullptr;
    }
    LOGI("stopProxyEngine: engine stopped");
}
