#include <jni.h>
#include <string>

extern "C"
jstring
Java_io_pijun_george_MainActivity_stringFromJNI(
        JNIEnv* env,
        jobject obj) {
    std::string hello = "Hello from C++";
    return env->NewStringUTF(hello.c_str());
}