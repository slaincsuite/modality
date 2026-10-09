package cn.slainc.crypto.symmetric;

import cn.slainc.crypto.CryptoException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;


class WeakAlgorithmPolicyTest {

    @AfterEach
    void reset() {
        WeakAlgorithmPolicy.allowWeakAlgorithms(false);
    }

    @Test
    @DisplayName("默认禁止 DES 与 ECB 等弱原语，并给出中文可操作提示")
    void weakAlgorithmsRejectedByDefault() {
        WeakAlgorithmPolicy.allowWeakAlgorithms(false);

        CryptoException des = assertThrows(CryptoException.class, DesUtils::generateKey);
        assertThat(des.getMessage()).contains("弱算法").contains(WeakAlgorithmPolicy.OPT_IN_PROPERTY);

        CryptoException ecb = assertThrows(CryptoException.class,
                () -> AesUtils.encrypt(CipherMode.ECB, Padding.PKCS5, new byte[16], null, new byte[16]));
        assertThat(ecb.getMessage()).contains("ECB");
    }

    @Test
    @DisplayName("显式开启后弱原语可用，安全模式不受影响")
    void weakAlgorithmsAllowedAfterOptIn() {
        WeakAlgorithmPolicy.allowWeakAlgorithms(false);
        assertNotNull(AesUtils.encrypt(CipherMode.GCM, Padding.NONE, new byte[16], new byte[12], new byte[16]));

        WeakAlgorithmPolicy.allowWeakAlgorithms(true);
        assertNotNull(DesUtils.generateKey());
        assertNotNull(AesUtils.encrypt(CipherMode.ECB, Padding.PKCS5, new byte[16], null, new byte[16]));
    }
}
