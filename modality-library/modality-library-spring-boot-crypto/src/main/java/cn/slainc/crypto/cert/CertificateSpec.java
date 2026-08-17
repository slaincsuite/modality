package cn.slainc.crypto.cert;

import cn.slainc.crypto.CryptoException;
import java.math.BigInteger;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;

public record CertificateSpec(String subject, Duration validity, BigInteger serial, boolean certificateAuthority, int pathLength,
                              List<String> subjectAlternativeNames, List<String> keyUsages, List<String> extendedKeyUsages) {

    public CertificateSpec {
        if (subject == null || subject.isBlank()) {
            throw new CryptoException("证书主体不能为空");
        }
        validity = validity == null ? Duration.ofDays(365) : validity;
        subjectAlternativeNames = subjectAlternativeNames == null ? List.of() : List.copyOf(subjectAlternativeNames);
        keyUsages = keyUsages == null ? List.of() : List.copyOf(keyUsages);
        extendedKeyUsages = extendedKeyUsages == null ? List.of() : List.copyOf(extendedKeyUsages);
    }

    public static CertificateSpec of(String subject, Duration validity) {
        return new CertificateSpec(subject, validity, null, false, -1, List.of(), List.of(), List.of());
    }

    public static CertificateSpec of(String subject, int days) {
        return of(subject, Duration.ofDays(days));
    }

    public CertificateSpec withSerial(BigInteger value) {
        return new CertificateSpec(subject, validity, value, certificateAuthority, pathLength, subjectAlternativeNames, keyUsages, extendedKeyUsages);
    }

    public CertificateSpec withSubject(String value) {
        return new CertificateSpec(value, validity, serial, certificateAuthority, pathLength, subjectAlternativeNames, keyUsages, extendedKeyUsages);
    }

    public CertificateSpec withValidity(Duration value) {
        return new CertificateSpec(subject, value, serial, certificateAuthority, pathLength, subjectAlternativeNames, keyUsages, extendedKeyUsages);
    }

    public CertificateSpec asCertificateAuthority(int length) {
        return new CertificateSpec(subject, validity, serial, true, length, subjectAlternativeNames, keyUsages, extendedKeyUsages);
    }

    public CertificateSpec withSubjectAlternativeNames(String... names) {
        return new CertificateSpec(subject, validity, serial, certificateAuthority, pathLength, Arrays.asList(names), keyUsages, extendedKeyUsages);
    }

    public CertificateSpec withKeyUsages(String... usages) {
        return new CertificateSpec(subject, validity, serial, certificateAuthority, pathLength, subjectAlternativeNames, Arrays.asList(usages), extendedKeyUsages);
    }

    public CertificateSpec withExtendedKeyUsages(String... usages) {
        return new CertificateSpec(subject, validity, serial, certificateAuthority, pathLength, subjectAlternativeNames, keyUsages, Arrays.asList(usages));
    }

    public CertificateSpec withServerAuth() {
        return withExtendedKeyUsages("serverAuth");
    }
}
