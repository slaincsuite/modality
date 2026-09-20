package cn.slainc.thirdauth.binding;

import cn.slainc.thirdauth.ThirdPartyPlatform;

import java.util.List;
import java.util.Optional;

public interface ThirdAccountBindingStore {

    ThirdAccountBinding save(ThirdAccountBinding binding);

    Optional<ThirdAccountBinding> findActive(ThirdPartyPlatform platform, String openId);

    Optional<ThirdAccountBinding> findByUnionId(ThirdPartyPlatform platform, String unionId);

    List<ThirdAccountBinding> listByUser(String userId);

    List<ThirdAccountBinding> listAll();
}
