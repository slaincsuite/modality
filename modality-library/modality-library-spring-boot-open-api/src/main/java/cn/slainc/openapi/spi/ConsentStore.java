package cn.slainc.openapi.spi;

import cn.slainc.openapi.domain.ConsentRecord;

import java.util.List;
import java.util.Optional;

public interface ConsentStore {

    ConsentRecord save(ConsentRecord record);

    Optional<ConsentRecord> find(String userId, String appId);

    List<ConsentRecord> listByUser(String userId);

    List<ConsentRecord> listByApp(String appId);

    void delete(String id);
}
