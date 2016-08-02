#include <jni.h>
#include <android/log.h>
#include <sodium.h>

unsigned char* ucharArray(JNIEnv *env, jbyteArray array) {
    int len = (*env)->GetArrayLength(env, array);
    unsigned char* buf = malloc(len * sizeof(unsigned char));
    (*env)->GetByteArrayRegion(env, array, 0, len, (jbyte*)buf);
    return buf;
}

jbyteArray byteArray(JNIEnv *env, unsigned char *buf, int len) {
    jbyteArray array = (*env)->NewByteArray(env, len);
    (*env)->SetByteArrayRegion(env, array, 0, len, (jbyte*)buf);
    return array;
}

// Sodium.init()
JNIEXPORT jint Java_io_pijun_george_Sodium_init(JNIEnv *env, jclass cls) {
    return sodium_init();
}

// Sodium.createKeyFromPassword(int keySizeBytes, byte[] password, byte[] salt)
JNIEXPORT jbyteArray JNICALL Java_io_pijun_george_Sodium_createKeyFromPassword(
    JNIEnv *env, jclass cls, jint keySizeBytes, jbyteArray password, jbyteArray salt) {
    if (password == NULL) {
        __android_log_print(ANDROID_LOG_INFO, "Pijun", "createKeyFromPassword: password is NULL. returning");
        return NULL;
    }
    if (salt == NULL) {
        __android_log_print(ANDROID_LOG_INFO, "Pijun", "createKeyFromPassword: salt is NULL. returning");
        return NULL;
    }


    unsigned char* passwordUChar = ucharArray(env, password);
    unsigned char* saltUChar = ucharArray(env, salt);
    unsigned char key[keySizeBytes];
    int result = crypto_pwhash(key,
                               keySizeBytes,
                               (const char*)passwordUChar,
                               (*env)->GetArrayLength(env, password),
                               saltUChar,
                               crypto_pwhash_OPSLIMIT_INTERACTIVE,
                               crypto_pwhash_MEMLIMIT_INTERACTIVE,
                               crypto_pwhash_ALG_DEFAULT);
    free(passwordUChar);
    free(saltUChar);
    if (result != 0) {
        __android_log_print(ANDROID_LOG_INFO, "Pijun", "createKeyFromPassword returned non-zero value: %d", result);
        return NULL;
    }

    jbyteArray keyBytes = byteArray(env, key, keySizeBytes);

    return keyBytes;
}

// Sodium.generateKeyPair(KeyPair kp)
JNIEXPORT jint JNICALL Java_io_pijun_george_Sodium_generateKeyPair(
    JNIEnv *env, jclass cls, jobject keyPair) {
    unsigned char pubKey[crypto_box_PUBLICKEYBYTES];
    unsigned char secKey[crypto_box_SECRETKEYBYTES];
    int result = crypto_box_keypair(pubKey, secKey);
    if (result != 0) {
        return result;
    }

    jbyteArray pubKeyArray = byteArray(env, pubKey, crypto_box_PUBLICKEYBYTES);
    jbyteArray secKeyArray = byteArray(env, secKey, crypto_box_SECRETKEYBYTES);

    jclass keyPairClass = (*env)->GetObjectClass(env, keyPair);
    jfieldID pubKeyId = (*env)->GetFieldID(env, keyPairClass, "publicKey", "[B");
    (*env)->SetObjectField(env, keyPair, pubKeyId, pubKeyArray);
    jfieldID secKeyId = (*env)->GetFieldID(env, keyPairClass, "secretKey", "[B");
    (*env)->SetObjectField(env, keyPair, secKeyId, secKeyArray);

    return 0;
}

// Sodium.secretKeyEncrypt(byte[] msg, byte[] key)
JNIEXPORT jobject JNICALL Java_io_pijun_george_Sodium_secretKeyEncrypt(
    JNIEnv *env, jclass cls, jbyteArray msg, jbyteArray key) {
    int msgLen = (*env)->GetArrayLength(env, msg);
    int cipherTextLen = msgLen + crypto_secretbox_MACBYTES;
    unsigned char cipherText[cipherTextLen];
    unsigned char* msgUChar = ucharArray(env, msg);
    unsigned char nonceUChar[crypto_secretbox_NONCEBYTES];
    unsigned char* keyUChar = ucharArray(env, key);
    randombytes_buf(nonceUChar, crypto_secretbox_NONCEBYTES);
    int result = crypto_secretbox_easy((unsigned char*)cipherText,
                                       msgUChar,
                                       msgLen,
                                       nonceUChar,
                                       keyUChar);
    free(msgUChar);
    free(keyUChar);
    if (result != 0) {
        return NULL;
    }

    jclass skemCls = (*env)->FindClass(env, "io/pijun/george/crypto/SecretKeyEncryptedMessage");
    jmethodID constructor = (*env)->GetMethodID(env, skemCls, "<init>", "()V");
    jobject skem = (*env)->NewObject(env, skemCls, constructor);

    jfieldID cipherTextFieldId = (*env)->GetFieldID(env, skemCls, "cipherText", "[B");
    jbyteArray cipherTextByteArray = byteArray(env, (unsigned char*)cipherText, cipherTextLen);
    (*env)->SetObjectField(env, skem, cipherTextFieldId, cipherTextByteArray);

    jfieldID nonceFieldId = (*env)->GetFieldID(env, skemCls, "nonce", "[B");
    jbyteArray nonceByteArray = byteArray(env, (unsigned char*)nonceUChar, crypto_secretbox_NONCEBYTES);
    (*env)->SetObjectField(env, skem, nonceFieldId, nonceByteArray);

    return skem;
}

// Sodium.secretKeyDecrypt(byte[] cipherText, byte[] nonce, byte[] key)
JNIEXPORT jbyteArray JNICALL Java_io_pijun_george_Sodium_secretKeyDecrypt(
    JNIEnv *env, jclass cls, jbyteArray cipherText, jbyteArray nonce, jbyteArray key) {
    int msgLen = (*env)->GetArrayLength(env, cipherText) - crypto_secretbox_MACBYTES;
    if (msgLen < 1) {
        __android_log_print(ANDROID_LOG_INFO, "Pijun", "secretKeyDecrypt: message is too short");
        return NULL;
    }

    unsigned char msgUChar[msgLen];
    unsigned char* cipherTextUChar = ucharArray(env, cipherText);
    unsigned char* nonceUChar = ucharArray(env, nonce);
    unsigned char* keyUChar = ucharArray(env, key);
    int result = crypto_secretbox_open_easy(msgUChar,
                                            cipherTextUChar,
                                            (*env)->GetArrayLength(env, cipherText),
                                            nonceUChar,
                                            keyUChar);
    free(cipherTextUChar);
    free(nonceUChar);
    free(keyUChar);
    if (result != 0) {
        __android_log_print(ANDROID_LOG_INFO, "Pijun", "secretKeyDecrypt: non-zero result (%d)", result);
        return NULL;
    }

    jbyteArray msg = byteArray(env, msgUChar, msgLen);
    return msg;
}

// Sodium.publicKeyEncrypt(byte[] msg, byte[] rcvrPubKey, byte[] sndrSecKey)
JNIEXPORT jobject JNICALL Java_io_pijun_george_Sodium_publicKeyEncrypt(
    JNIEnv *env, jclass cls, jbyteArray msg, jbyteArray rcvrPubKey, jbyteArray sndrSecKey) {
    int msgLen = (*env)->GetArrayLength(env, msg);
    int cipherTextLen = crypto_box_MACBYTES + msgLen;
    unsigned char cipherText[cipherTextLen];
    unsigned char nonce[crypto_box_NONCEBYTES];
    randombytes_buf(nonce, crypto_box_NONCEBYTES);
    unsigned char* msgUChar = ucharArray(env, msg);
    unsigned char* rcvrKeyUChar = ucharArray(env, rcvrPubKey);
    unsigned char* sndrKeyUChar = ucharArray(env, sndrSecKey);
    int result = crypto_box_easy(cipherText,
                                 msgUChar,
                                 (*env)->GetArrayLength(env, msg),
                                 nonce,
                                 rcvrKeyUChar,
                                 sndrKeyUChar);
    free(msgUChar);
    free(rcvrKeyUChar);
    free(sndrKeyUChar);
    if (result != 0) {
        __android_log_print(ANDROID_LOG_INFO, "Pijun", "publicKeyEncrypt: non-zero result (%d)", result);
        return NULL;
    }

    jclass skemCls = (*env)->FindClass(env, "io/pijun/george/crypto/SecretKeyEncryptedMessage");
    jmethodID constructor = (*env)->GetMethodID(env, skemCls, "<init>", "()V");
    jobject skem = (*env)->NewObject(env, skemCls, constructor);

    jfieldID cipherTextFieldId = (*env)->GetFieldID(env, skemCls, "cipherText", "[B");
    jbyteArray cipherTextByteArray = byteArray(env, cipherText, cipherTextLen);
    (*env)->SetObjectField(env, skem, cipherTextFieldId, cipherTextByteArray);

    jfieldID nonceFieldId = (*env)->GetFieldID(env, skemCls, "nonce", "[B");
    jbyteArray nonceByteArray = byteArray(env, nonce, crypto_box_NONCEBYTES);
    (*env)->SetObjectField(env, skem, nonceFieldId, nonceByteArray);

    return skem;
}

JNIEXPORT jbyteArray JNICALL Java_io_pijun_george_Sodium_publicKeyDecrypt(
    JNIEnv *env, jclass cls, jbyteArray cipherText, jbyteArray nonce, jbyteArray senderPubKey, jbyteArray receiverSecretKey) {
    int cipherTextLen = (*env)->GetArrayLength(env, cipherText);
    int msgLen = cipherTextLen - crypto_box_MACBYTES;
    if (msgLen < 1) {
        __android_log_print(ANDROID_LOG_INFO, "Pijun", "publicKeyDecrypt: message is too short");
        return NULL;
    }
    unsigned char msg[msgLen];
    unsigned char* cipherTextUChar = ucharArray(env, cipherText);
    unsigned char* nonceUChar = ucharArray(env, nonce);
    unsigned char* pubKeyUChar = ucharArray(env, senderPubKey);
    unsigned char* secKeyUChar = ucharArray(env, receiverSecretKey);
    int result = crypto_box_open_easy(msg,
                                      cipherTextUChar,
                                      cipherTextLen,
                                      nonceUChar,
                                      pubKeyUChar,
                                      secKeyUChar);
    free(cipherTextUChar);
    free(nonceUChar);
    free(pubKeyUChar);
    free(secKeyUChar);
    if (result != 0) {
        __android_log_print(ANDROID_LOG_INFO, "Pijun", "publicKeyDecrypt: non-zero result (%d)", result);
        return NULL;
    }

    return byteArray(env, msg, msgLen);
}