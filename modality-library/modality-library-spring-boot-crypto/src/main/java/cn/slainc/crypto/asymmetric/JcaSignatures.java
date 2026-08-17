package cn.slainc.crypto.asymmetric;

import cn.slainc.crypto.CryptoException;
import cn.slainc.crypto.CryptoProviders;
import cn.slainc.crypto.random.SecureRandomUtils;
import java.security.GeneralSecurityException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.MGF1ParameterSpec;
import java.security.spec.PSSParameterSpec;

public final class JcaSignatures {

    private JcaSignatures() {
    }

    public static byte[] sign(String jcaName, String pssDigest, int pssSaltLength, PrivateKey privateKey, byte[] data) {
        try {
            Signature signature = signature(jcaName);
            signature.initSign(privateKey, SecureRandomUtils.secureRandom());
            applyParameters(signature, pssDigest, pssSaltLength);
            signature.update(data);
            return signature.sign();
        } catch (GeneralSecurityException exception) {
            throw new CryptoException("签名失败，算法：" + jcaName, exception);
        }
    }

    public static boolean verify(String jcaName, String pssDigest, int pssSaltLength, PublicKey publicKey, byte[] data, byte[] signatureBytes) {
        try {
            Signature signature = signature(jcaName);
            signature.initVerify(publicKey);
            applyParameters(signature, pssDigest, pssSaltLength);
            signature.update(data);
            return signature.verify(signatureBytes);
        } catch (GeneralSecurityException exception) {
            throw new CryptoException("验签失败，算法：" + jcaName, exception);
        }
    }

    private static Signature signature(String jcaName) throws GeneralSecurityException {
        return Signature.getInstance(jcaName, CryptoProviders.bouncyCastle());
    }

    private static void applyParameters(Signature signature, String pssDigest, int pssSaltLength) throws GeneralSecurityException {
        if (pssDigest != null) {
            signature.setParameter(new PSSParameterSpec(pssDigest, "MGF1", new MGF1ParameterSpec(pssDigest), pssSaltLength, 1));
        }
    }
}
