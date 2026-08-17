package cn.slainc.crypto.stream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cn.slainc.crypto.CryptoException;
import cn.slainc.crypto.random.SecureRandomUtils;
import cn.slainc.crypto.sm.Sm4Utils;
import cn.slainc.crypto.symmetric.AesKeySize;
import cn.slainc.crypto.symmetric.AesUtils;
import cn.slainc.crypto.symmetric.CipherMode;
import cn.slainc.crypto.symmetric.Padding;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class StreamCryptoTest {

    @Test
    void roundTripsVariousSizesWithAes() {
        SecretKey key = AesUtils.generateKey(AesKeySize.AES_256);
        for (int size : new int[]{0, 1, 1000, 65535, 65536, 65537, 200000}) {
            byte[] data = SecureRandomUtils.nextBytes(size);
            byte[] encrypted = StreamCrypto.encrypt(data, key, StreamCryptoAlgorithm.AES_GCM);
            assertTrue(encrypted.length > size, "size=" + size);
            assertNotEquals(0, encrypted.length);
            assertArrayEquals(data, StreamCrypto.decrypt(encrypted, key), "size=" + size);
        }
    }

    @Test
    void roundTripsWithSm4AndCustomChunkSize() throws IOException {
        SecretKey key = new SecretKeySpec(SecureRandomUtils.nextBytes(16), "SM4");
        byte[] data = SecureRandomUtils.nextBytes(300000);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (EncryptingOutputStream encrypting = new EncryptingOutputStream(output, key, StreamCryptoAlgorithm.SM4_GCM, 4096)) {
            encrypting.write(data);
            encrypting.write(data, 0, 10);
            assertEquals(4096, encrypting.chunkSize());
            assertTrue(encrypting.chunkCount() > 0);
        }
        byte[] encrypted = output.toByteArray();
        byte[] decrypted;
        try (DecryptingInputStream decrypting = new DecryptingInputStream(new ByteArrayInputStream(encrypted), key)) {
            assertEquals(StreamCryptoAlgorithm.SM4_GCM, decrypting.algorithm());
            decrypted = decrypting.readAllBytes();
        }
        assertEquals(data.length + 10, decrypted.length);
        ByteArrayOutputStream plain = new ByteArrayOutputStream();
        StreamCrypto.decrypt(new ByteArrayInputStream(encrypted), plain, key);
        assertEquals(data.length + 10, plain.size());
        assertArrayEquals(decrypted, plain.toByteArray());
    }

    @Test
    void streamsThroughCustomStreams() throws IOException {
        SecretKey key = AesUtils.generateKey(AesKeySize.AES_128);
        byte[] data = "modality 流式加密".getBytes(StandardCharsets.UTF_8);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (EncryptingOutputStream encrypting = new EncryptingOutputStream(output, key, StreamCryptoAlgorithm.AES_GCM)) {
            for (byte value : data) {
                encrypting.write(value);
            }
            encrypting.flush();
        }
        try (DecryptingInputStream decrypting = new DecryptingInputStream(new ByteArrayInputStream(output.toByteArray()), key)) {
            assertArrayEquals(data, decrypting.readAllBytes());
            assertEquals(-1, decrypting.read());
            assertEquals(0, decrypting.read(new byte[4], 0, 0));
        }
        try (EncryptingOutputStream closed = new EncryptingOutputStream(new ByteArrayOutputStream(), key, StreamCryptoAlgorithm.AES_GCM)) {
            closed.close();
            assertThrows(IOException.class, () -> closed.write(1));
            closed.close();
        }
    }

    @Test
    void supportsPlainAndSm4ByteHelpers() {
        SecretKey sm4Key = new SecretKeySpec(SecureRandomUtils.nextBytes(16), "SM4");
        byte[] data = SecureRandomUtils.nextBytes(4096);
        byte[] encrypted = StreamCrypto.encrypt(data, sm4Key, StreamCryptoAlgorithm.SM4_GCM);
        assertArrayEquals(data, StreamCrypto.decrypt(encrypted, sm4Key));
        byte[] viaSm4 = Sm4Utils.encryptGcm(sm4Key.getEncoded(), data);
        assertArrayEquals(data, Sm4Utils.decryptGcm(sm4Key.getEncoded(), viaSm4));
    }

    @Test
    void detectsTamperedAndTruncatedStreams() {
        SecretKey key = AesUtils.generateKey(AesKeySize.AES_256);
        byte[] data = SecureRandomUtils.nextBytes(70000);
        byte[] encrypted = StreamCrypto.encrypt(data, key, StreamCryptoAlgorithm.AES_GCM);
        byte[] tampered = encrypted.clone();
        tampered[tampered.length - 20] ^= 0x01;
        assertThrows(CryptoException.class, () -> StreamCrypto.decrypt(tampered, key));
        byte[] truncated = Arrays.copyOf(encrypted, encrypted.length - 40);
        assertThrows(CryptoException.class, () -> StreamCrypto.decrypt(truncated, key));
        SecretKey other = AesUtils.generateKey(AesKeySize.AES_256);
        assertThrows(CryptoException.class, () -> StreamCrypto.decrypt(encrypted, other));
        assertThrows(CryptoException.class, () -> StreamCrypto.decrypt(new byte[]{1, 2, 3, 4}, key));
        byte[] wrongMagic = encrypted.clone();
        wrongMagic[0] = 'X';
        assertThrows(CryptoException.class, () -> StreamCrypto.decrypt(wrongMagic, key));
        byte[] wrongVersion = encrypted.clone();
        wrongVersion[4] = 9;
        assertThrows(CryptoException.class, () -> StreamCrypto.decrypt(wrongVersion, key));
        byte[] wrongAlgorithm = encrypted.clone();
        wrongAlgorithm[5] = 9;
        assertThrows(CryptoException.class, () -> StreamCrypto.decrypt(wrongAlgorithm, key));
    }

    @Test
    void encryptsFiles(@TempDir Path directory) throws IOException {
        SecretKey key = AesUtils.generateKey(AesKeySize.AES_256);
        byte[] data = SecureRandomUtils.nextBytes(150000);
        Path source = directory.resolve("source.bin");
        Path encrypted = directory.resolve("encrypted.bin");
        Path decrypted = directory.resolve("decrypted.bin");
        Files.write(source, data);
        StreamCrypto.encrypt(source, encrypted, key, StreamCryptoAlgorithm.AES_GCM);
        assertTrue(Files.size(encrypted) > data.length);
        StreamCrypto.decrypt(encrypted, decrypted, key);
        assertArrayEquals(data, Files.readAllBytes(decrypted));
        assertThrows(CryptoException.class, () -> StreamCrypto.decrypt(directory.resolve("missing.bin"), decrypted, key));
    }

    @Test
    void validatesArguments() {
        SecretKey aesKey = AesUtils.generateKey(AesKeySize.AES_256);
        assertThrows(CryptoException.class, () -> new EncryptingOutputStream(new ByteArrayOutputStream(), aesKey, StreamCryptoAlgorithm.SM4_GCM));
        assertThrows(CryptoException.class, () -> new EncryptingOutputStream(null, aesKey, StreamCryptoAlgorithm.AES_GCM));
        assertThrows(CryptoException.class, () -> new EncryptingOutputStream(new ByteArrayOutputStream(), null, StreamCryptoAlgorithm.AES_GCM));
        assertThrows(CryptoException.class, () -> new EncryptingOutputStream(new ByteArrayOutputStream(), aesKey, StreamCryptoAlgorithm.AES_GCM, 8));
        assertThrows(CryptoException.class, () -> new DecryptingInputStream(null, aesKey));
        assertThrows(CryptoException.class, () -> new DecryptingInputStream(new ByteArrayInputStream(new byte[0]), aesKey));
        assertThrows(CryptoException.class, () -> StreamCryptoAlgorithm.fromId(9));
        assertThrows(CryptoException.class, () -> Sm4Utils.encrypt(CipherMode.GCM, Padding.NONE, new byte[8], new byte[12], new byte[4]));
        assertThrows(IOException.class, () -> new EncryptingOutputStream(new ByteArrayOutputStream(), aesKey, StreamCryptoAlgorithm.AES_GCM).write(null, 0, 1));
    }
}
