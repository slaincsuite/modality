package cn.slainc.crypto.cert;

import java.math.BigInteger;
import java.time.Instant;
import java.util.List;

public record CertificateInfo(String subject, String issuer, BigInteger serialNumber, Instant notBefore, Instant notAfter,
                              String signatureAlgorithm, String publicKeyAlgorithm, int version, boolean certificateAuthority,
                              List<String> subjectAlternativeNames) {

    public boolean isValidAt(Instant instant) {
        return !instant.isBefore(notBefore) && !instant.isAfter(notAfter);
    }

    public boolean isExpired() {
        return isExpired(Instant.now());
    }

    public boolean isExpired(Instant instant) {
        return instant.isAfter(notAfter);
    }

    public boolean isSelfSigned() {
        return subject.equals(issuer);
    }
}
