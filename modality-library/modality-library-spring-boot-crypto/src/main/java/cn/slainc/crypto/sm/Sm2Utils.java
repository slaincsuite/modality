package cn.slainc.crypto.sm;

import cn.slainc.crypto.CryptoException;
import cn.slainc.crypto.CryptoProviders;
import cn.slainc.crypto.codec.Base64Utils;
import cn.slainc.crypto.codec.HexUtils;
import cn.slainc.crypto.random.SecureRandomUtils;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.spec.ECGenParameterSpec;
import org.bouncycastle.crypto.DataLengthException;
import org.bouncycastle.crypto.InvalidCipherTextException;
import org.bouncycastle.crypto.engines.SM2Engine;
import org.bouncycastle.crypto.params.AsymmetricKeyParameter;
import org.bouncycastle.crypto.params.ECPrivateKeyParameters;
import org.bouncycastle.crypto.params.ECPublicKeyParameters;
import org.bouncycastle.crypto.params.ParametersWithID;
import org.bouncycastle.crypto.params.ParametersWithRandom;
import org.bouncycastle.crypto.signers.PlainDSAEncoding;
import org.bouncycastle.crypto.signers.SM2Signer;
import org.bouncycastle.crypto.signers.StandardDSAEncoding;
import org.bouncycastle.crypto.util.PrivateKeyFactory;
import org.bouncycastle.crypto.util.PublicKeyFactory;

public final class Sm2Utils {

    public static final String CURVE_NAME = "sm2p256v1";

    public static final byte[] DEFAULT_ID = "1234567812345678".getBytes(StandardCharsets.UTF_8);

    public static final int SIGNATURE_LENGTH = 64;

    private Sm2Utils() {
    }

    public static KeyPair generateKeyPair() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("EC", CryptoProviders.bouncyCastle());
            generator.initialize(new ECGenParameterSpec(CURVE_NAME), SecureRandomUtils.secureRandom());
            return generator.generateKeyPair();
        } catch (GeneralSecurityException exception) {
            throw new CryptoException("无法生成 SM2 密钥对", exception);
        }
    }

    public static byte[] encrypt(PublicKey publicKey, byte[] data) {
        return encrypt(publicKey, data, Sm2Mode.C1C3C2);
    }

    public static byte[] encrypt(PublicKey publicKey, byte[] data, Sm2Mode mode) {
        SM2Engine engine = new SM2Engine(engineMode(mode));
        engine.init(true, new ParametersWithRandom(publicKeyParameter(publicKey), SecureRandomUtils.secureRandom()));
        try {
            return engine.processBlock(data, 0, data.length);
        } catch (InvalidCipherTextException | DataLengthException exception) {
            throw new CryptoException("SM2 加密失败", exception);
        }
    }

    public static byte[] decrypt(PrivateKey privateKey, byte[] cipherText) {
        return decrypt(privateKey, cipherText, Sm2Mode.C1C3C2);
    }

    public static byte[] decrypt(PrivateKey privateKey, byte[] cipherText, Sm2Mode mode) {
        SM2Engine engine = new SM2Engine(engineMode(mode));
        engine.init(false, privateKeyParameter(privateKey));
        try {
            return engine.processBlock(cipherText, 0, cipherText.length);
        } catch (InvalidCipherTextException exception) {
            throw new CryptoException("SM2 解密失败", exception);
        }
    }

    public static String encryptBase64(PublicKey publicKey, byte[] data) {
        return Base64Utils.encode(encrypt(publicKey, data));
    }

    public static byte[] decryptBase64(PrivateKey privateKey, String cipherText) {
        return decrypt(privateKey, Base64Utils.decode(cipherText));
    }

    public static byte[] sign(PrivateKey privateKey, byte[] data) {
        return sign(privateKey, data, DEFAULT_ID);
    }

    public static byte[] sign(PrivateKey privateKey, byte[] data, byte[] id) {
        return sign(privateKey, data, id, SecureRandomUtils.secureRandom());
    }

    public static byte[] sign(PrivateKey privateKey, byte[] data, byte[] id, SecureRandom random) {
        SM2Signer signer = new SM2Signer(PlainDSAEncoding.INSTANCE);
        signer.init(true, withId(new ParametersWithRandom(privateKeyParameter(privateKey), random), id));
        signer.update(data, 0, data.length);
        try {
            return signer.generateSignature();
        } catch (org.bouncycastle.crypto.CryptoException exception) {
            throw new CryptoException("SM2 签名失败", exception);
        }
    }

    public static boolean verify(PublicKey publicKey, byte[] data, byte[] signature) {
        return verify(publicKey, data, signature, DEFAULT_ID);
    }

    public static boolean verify(PublicKey publicKey, byte[] data, byte[] signature, byte[] id) {
        return verify(publicKey, data, signature, id, PlainDSAEncoding.INSTANCE);
    }

    public static byte[] signDer(PrivateKey privateKey, byte[] data) {
        return signDer(privateKey, data, DEFAULT_ID);
    }

    public static byte[] signDer(PrivateKey privateKey, byte[] data, byte[] id) {
        return signDer(privateKey, data, id, SecureRandomUtils.secureRandom());
    }

    public static byte[] signDer(PrivateKey privateKey, byte[] data, byte[] id, SecureRandom random) {
        SM2Signer signer = new SM2Signer(StandardDSAEncoding.INSTANCE);
        signer.init(true, withId(new ParametersWithRandom(privateKeyParameter(privateKey), random), id));
        signer.update(data, 0, data.length);
        try {
            return signer.generateSignature();
        } catch (org.bouncycastle.crypto.CryptoException exception) {
            throw new CryptoException("SM2 签名失败", exception);
        }
    }

    public static boolean verifyDer(PublicKey publicKey, byte[] data, byte[] signature) {
        return verifyDer(publicKey, data, signature, DEFAULT_ID);
    }

    public static boolean verifyDer(PublicKey publicKey, byte[] data, byte[] signature, byte[] id) {
        return verify(publicKey, data, signature, id, StandardDSAEncoding.INSTANCE);
    }

    public static String signBase64(PrivateKey privateKey, byte[] data) {
        return Base64Utils.encode(sign(privateKey, data));
    }

    public static boolean verifyBase64(PublicKey publicKey, byte[] data, String signature) {
        return verify(publicKey, data, Base64Utils.decode(signature));
    }

    public static String signHex(PrivateKey privateKey, byte[] data) {
        return HexUtils.encode(sign(privateKey, data));
    }

    public static boolean verifyHex(PublicKey publicKey, byte[] data, String signature) {
        return verify(publicKey, data, HexUtils.decode(signature));
    }

    private static boolean verify(PublicKey publicKey, byte[] data, byte[] signature, byte[] id, org.bouncycastle.crypto.signers.DSAEncoding encoding) {
        SM2Signer signer = new SM2Signer(encoding);
        signer.init(false, withId(publicKeyParameter(publicKey), id));
        signer.update(data, 0, data.length);
        return signer.verifySignature(signature);
    }

    private static org.bouncycastle.crypto.CipherParameters withId(org.bouncycastle.crypto.CipherParameters parameters, byte[] id) {
        byte[] value = id == null ? DEFAULT_ID : id;
        return new ParametersWithID(parameters, value);
    }

    private static SM2Engine.Mode engineMode(Sm2Mode mode) {
        return mode == Sm2Mode.C1C2C3 ? SM2Engine.Mode.C1C2C3 : SM2Engine.Mode.C1C3C2;
    }

    private static ECPrivateKeyParameters privateKeyParameter(PrivateKey privateKey) {
        AsymmetricKeyParameter parameter = decode(privateKey.getEncoded(), true);
        if (!(parameter instanceof ECPrivateKeyParameters ecParameter)) {
            throw new CryptoException("不是 EC 私钥：" + privateKey.getAlgorithm());
        }
        return ecParameter;
    }

    private static ECPublicKeyParameters publicKeyParameter(PublicKey publicKey) {
        AsymmetricKeyParameter parameter = decode(publicKey.getEncoded(), false);
        if (!(parameter instanceof ECPublicKeyParameters ecParameter)) {
            throw new CryptoException("不是 EC 公钥：" + publicKey.getAlgorithm());
        }
        return ecParameter;
    }

    private static AsymmetricKeyParameter decode(byte[] encoded, boolean privateKey) {
        try {
            return privateKey ? PrivateKeyFactory.createKey(encoded) : PublicKeyFactory.createKey(encoded);
        } catch (IOException exception) {
            throw new CryptoException("无法解析 SM2 密钥数据", exception);
        }
    }
}
