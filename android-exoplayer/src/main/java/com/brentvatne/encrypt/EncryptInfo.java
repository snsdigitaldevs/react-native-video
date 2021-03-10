package com.brentvatne.encrypt;

import androidx.annotation.Nullable;

public class EncryptInfo {

    private byte[] key;

    private byte[] iv;

    private EncryptInfo() {
    }

    public static EncryptInfo getInstance() {
        return StaticSingletonHolder.instance;
    }

    private static class StaticSingletonHolder {
        private static final EncryptInfo instance = new EncryptInfo();
    }

    public void setEncryptInfo(@Nullable String key, @Nullable String iv) {
        if (key != null) {
            this.key = key.getBytes();
        }
        if (iv != null) {
            this.iv = iv.getBytes();
        }
    }

    @Nullable
    public byte[] getKey() {
        return key;
    }

    @Nullable
    public byte[] getIv() {
        return iv;
    }
}
