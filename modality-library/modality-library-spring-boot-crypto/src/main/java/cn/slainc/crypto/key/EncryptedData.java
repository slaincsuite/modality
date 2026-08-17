package cn.slainc.crypto.key;

public record EncryptedData(String keyId, String keyAlgorithm, byte[] wrappedKey, byte[] iv, byte[] cipherText) {

    public EncryptedData {
        wrappedKey = wrappedKey == null ? new byte[0] : wrappedKey.clone();
        iv = iv == null ? new byte[0] : iv.clone();
        cipherText = cipherText == null ? new byte[0] : cipherText.clone();
    }
}
