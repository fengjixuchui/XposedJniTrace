#include <libgen.h>
#include <jni.h>
#include <logging.h>
#include <string>
#include <map>
#include <list>
#include <cstring>
#include <cstdio>
#include <regex>
#include <cerrno>
#include <climits>
#include <iostream>
#include <fstream>

#include "parse.h"
#include "adapter.h"

#include "linker_callback.h"


bool isTraceIl2cpp = false;
const char *saveFilePath = nullptr;

jint JNICALL
JNI_OnLoad(JavaVM *vm, [[maybe_unused]] void *reserved) {
    LOG(INFO) << "JNI_OnLoad start [" << getprogname() << "]";
    mVm = vm;
    JNIEnv *env = nullptr;
    if (vm->GetEnv((void **) &env, JNI_VERSION_1_6) == JNI_OK) {
        mEnv = env;
        LOG(INFO) << "JNI_OnLoad start find class  ! ";

        //get is trace il2cpp config
        auto configClazz = (jclass) env->FindClass("com/zhenxi/il2cpptrace/il2cppTraceConfig");
        LOG(INFO) << "JNI_OnLoad find class success ! ";
        if (configClazz == nullptr) {
            LOG(INFO) << "JNI_OnLoad not find il2cppTraceConfig clazz ";
            return JNI_ERR;
        }
        //init native config
        jfieldID id1 = env->GetStaticFieldID(configClazz, "isTraceIl2cpp", "Z");
        jboolean ret = env->GetStaticBooleanField(configClazz, id1);
        isTraceIl2cpp = parse::jboolean2bool(ret);

        jfieldID id2 = env->GetStaticFieldID(configClazz,
                                             "hackSaveFilePath", "Ljava/lang/String;");
        jobject ret2 = env->GetStaticObjectField(configClazz, id2);
        saveFilePath = env->GetStringUTFChars((jstring) ret2, nullptr);
        //hook linker ,wait for the il2cpp.so to load
        linkerCallBack::hookLinkerCallBack();
        LOG(INFO) << ">>>>>>>>>>>>  JNI_OnLoad load success [" << getprogname() << "]";
        LOG(ERROR) << "config Into isTraceIl2cpp -> " <<
                   (isTraceIl2cpp ? "[true] " : "[false] ") << saveFilePath;
        return JNI_VERSION_1_6;
    }
    LOG(ERROR) << "JNI_OnLoad load fail ";
    return JNI_ERR;
}
