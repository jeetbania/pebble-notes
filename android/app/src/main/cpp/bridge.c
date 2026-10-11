#include <jni.h>
#include <stdint.h>
#include <stdlib.h>
#include <string.h>
#include "pebble.h"
static char *utf8(JNIEnv *e,jbyteArray b) { jsize n=(*e)->GetArrayLength(e,b); char *s=malloc(n+1); (*e)->GetByteArrayRegion(e,b,0,n,(jbyte*)s); s[n]=0; return s; }
static jbyteArray bytes(JNIEnv *e,const char *s) { size_t n=strlen(s); jbyteArray b=(*e)->NewByteArray(e,n); (*e)->SetByteArrayRegion(e,b,0,n,(const jbyte*)s); return b; }
JNIEXPORT jlong JNICALL Java_dev_leafnotes_NativeStore_open(JNIEnv *e,jobject o,jbyteArray p) { char *s=utf8(e,p); pebble_store *db=pebble_open(s); free(s); return (jlong)(intptr_t)db; }
JNIEXPORT jint JNICALL Java_dev_leafnotes_NativeStore_put(JNIEnv *e,jobject o,jlong h,jbyteArray b,jboolean remote) { char *s=utf8(e,b); int result=pebble_put((pebble_store*)(intptr_t)h,s,remote); free(s); return result; }
JNIEXPORT jbyteArray JNICALL Java_dev_leafnotes_NativeStore_list(JNIEnv *e,jobject o,jlong h,jboolean pending,jboolean heads) { char *s=pebble_list((pebble_store*)(intptr_t)h,pending,heads); if(!s) return NULL; jbyteArray result=bytes(e,s); pebble_free(s); return result; }
JNIEXPORT jbyteArray JNICALL Java_dev_leafnotes_NativeStore_error(JNIEnv *e,jobject o,jlong h) { return bytes(e,pebble_error((pebble_store*)(intptr_t)h)); }
JNIEXPORT jint JNICALL Java_dev_leafnotes_NativeStore_ack(JNIEnv *e,jobject o,jlong h,jbyteArray b) { char *s=utf8(e,b); int r=pebble_ack((pebble_store*)(intptr_t)h,s); free(s); return r; }
JNIEXPORT void JNICALL Java_dev_leafnotes_NativeStore_close(JNIEnv *e,jobject o,jlong h) { pebble_close((pebble_store*)(intptr_t)h); }
