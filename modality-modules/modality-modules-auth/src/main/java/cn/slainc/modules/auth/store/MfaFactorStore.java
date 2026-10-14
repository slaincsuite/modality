package cn.slainc.modules.auth.store;

import cn.slainc.mfa.spi.MfaFactorPort;
import cn.slainc.modules.auth.mfa.MfaFactorRecord;

public interface MfaFactorStore extends MfaFactorPort {

    MfaFactorRecord findRecord(Long userId, String factorType);

    MfaFactorRecord saveRecord(MfaFactorRecord record);
}
