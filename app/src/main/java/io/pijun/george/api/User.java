package io.pijun.george.api;

public class User {

    public String username;
    public byte[] passwordSalt;
    public String passwordHashAlgorithm;
    public long passwordHashOperationsLimit;
    public long passwordHashMemoryLimit;
    public byte[] publicKey;
    public byte[] wrappedSecretKey;
    public byte[] wrappedSecretKeyNonce;
    public byte[] wrappedSymmetricKey;
    public byte[] wrappedSymmetricKeyNonce;
    public String email;

}
