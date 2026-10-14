package cn.slainc.openapi.spi;

import cn.slainc.openapi.domain.ConsentRecord;

import java.util.List;

public interface ConsentService {

    ConsentRecord grant(String userId, String appId, List<String> scopes, String source);

    ConsentRecord revoke(String userId, String appId);

    ConsentRecord revokeScope(String userId, String appId, String scope);

    boolean isGranted(String userId, String appId, String scope);

    List<ConsentRecord> listByUser(String userId);

    List<ConsentRecord> listByApp(String appId);
}
