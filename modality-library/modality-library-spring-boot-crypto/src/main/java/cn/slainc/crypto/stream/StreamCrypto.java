package cn.slainc.crypto.stream;

import cn.slainc.crypto.CryptoException;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.crypto.SecretKey;

public final class StreamCrypto {

    private StreamCrypto() {
    }

    public static void encrypt(InputStream input, OutputStream output, SecretKey key, StreamCryptoAlgorithm algorithm) {
        try (EncryptingOutputStream encrypting = new EncryptingOutputStream(output, key, algorithm)) {
            input.transferTo(encrypting);
        } catch (IOException exception) {
            throw new CryptoException("流式加密失败", exception);
        }
    }

    public static void encrypt(InputStream input, OutputStream output, SecretKey key) {
        encrypt(input, output, key, StreamCryptoAlgorithm.AES_GCM);
    }

    public static void decrypt(InputStream input, OutputStream output, SecretKey key) {
        try (DecryptingInputStream decrypting = new DecryptingInputStream(input, key)) {
            decrypting.transferTo(output);
        } catch (IOException exception) {
            throw new CryptoException("流式解密失败", exception);
        }
    }

    public static void encrypt(Path source, Path target, SecretKey key, StreamCryptoAlgorithm algorithm) {
        try (InputStream input = new BufferedInputStream(Files.newInputStream(source));
             OutputStream output = new BufferedOutputStream(Files.newOutputStream(target))) {
            encrypt(input, output, key, algorithm);
        } catch (IOException exception) {
            throw new CryptoException("流式加密文件失败", exception);
        }
    }

    public static void decrypt(Path source, Path target, SecretKey key) {
        try (InputStream input = new BufferedInputStream(Files.newInputStream(source));
             OutputStream output = new BufferedOutputStream(Files.newOutputStream(target))) {
            decrypt(input, output, key);
        } catch (IOException exception) {
            throw new CryptoException("流式解密文件失败", exception);
        }
    }

    public static byte[] encrypt(byte[] data, SecretKey key, StreamCryptoAlgorithm algorithm) {
        java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream();
        encrypt(new java.io.ByteArrayInputStream(data), output, key, algorithm);
        return output.toByteArray();
    }

    public static byte[] decrypt(byte[] data, SecretKey key) {
        java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream();
        decrypt(new java.io.ByteArrayInputStream(data), output, key);
        return output.toByteArray();
    }
}
