#include "FakeCommon.h"
#include <stdlib.h>
#include <dlfcn.h>
#include <stdio.h>
#include <jni.h>

#include <android/log.h>
#define  TAG    "hello_load"
// 定义info信息
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO,TAG,__VA_ARGS__)
// 定义debug信息
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, TAG, __VA_ARGS__)
// 定义error信息
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR,TAG,__VA_ARGS__)

JNINativeMethod *dvm_dalvik_system_DexFile;
void (*openDexFile)(const u4* args, union JValue* pResult);

int lookup(JNINativeMethod *table, const char *name, const char *sig,
		void (**fnPtrout)(u4 const *, union JValue *)) {
	int i = 0;
	while (table[i].name != NULL) {
		LOGI("lookup %d %s", i, table[i].name);
		if ((strcmp(name, table[i].name) == 0)
				&& (strcmp(sig, table[i].signature) == 0)) {
			*fnPtrout = table[i].fnPtr;
			return 1;
		}
		i++;
	}
	return 0;
}

/* This function will be call when the library first be load.
 * You can do some init in the libray. return which version jni it support.
 */JNIEXPORT jint JNI_OnLoad(JavaVM* vm, void* reserved) {
	void *ldvm = (void*) dlopen("libdvm.so", RTLD_LAZY);
	dvm_dalvik_system_DexFile = (JNINativeMethod*) dlsym(ldvm,
			"dvm_dalvik_system_DexFile");
	if (0 == lookup(dvm_dalvik_system_DexFile, "openDexFile", "([B)I",
					&openDexFile)) {
		openDexFile = NULL;
		LOGE("method does not found ");
	} else {
		LOGI("method found ! HAVE_BIG_ENDIAN");
	}
	LOGI("ENDIANNESS is %c", ENDIANNESS);
	void *venv;
	LOGI("dufresne----->JNI_OnLoad!");
	if ((*vm)->GetEnv(vm, (void**) &venv, JNI_VERSION_1_4) != JNI_OK) {
		LOGE("dufresne--->ERROR: GetEnv failed");
		return -1;
	}
	return JNI_VERSION_1_4;
}

JNIEXPORT jint JNICALL Java_com_example_kyle_tools_NativeTool_loadDex(
		JNIEnv * env, jclass jv, jbyteArray dexArray, jlong dexLen) {
	// header+dex content


	u1 * olddata = (u1*) (*env)->GetByteArrayElements(env, dexArray, NULL);
	jlong dexLen2 = (*env)->GetArrayLength(env, dexArray);
	LOGI("call loadDex begin  3 %d, %d" , (jint)dexLen2, (jint)dexLen);
	char* arr;
	LOGI("call loadDex begin  3 %d" , (jint)dexLen2);
	arr = (char*) malloc(16 + dexLen2);
	ArrayObject *ao = (ArrayObject*) arr;
	ao->length = dexLen2;
	memcpy(arr + 16, olddata, dexLen2);
	u4 args[] = { (u4) ao };
	union JValue pResult;
	jint result;
	LOGI("call openDexFile 33...");
	if (openDexFile != NULL) {
		openDexFile(args, &pResult);
	} else {
		result = -1;
	}

	result = (jint) pResult.l;
	LOGI("Java_com_android_dexunshell_NativeTool_loadDex %d", result);
	return result;
}

