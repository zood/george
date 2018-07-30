package io.pijun.george.api;

import com.google.gson.annotations.SerializedName;

public class OutboundMessage {

    public byte[] cipherText;
    public byte[] nonce;
    public boolean urgent;
    @SerializedName("transient")
    public boolean isTransient;

}
