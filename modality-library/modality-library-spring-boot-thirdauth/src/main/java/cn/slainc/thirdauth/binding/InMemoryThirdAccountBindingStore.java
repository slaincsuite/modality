package cn.slainc.thirdauth.binding;

import cn.slainc.thirdauth.ThirdPartyPlatform;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public class InMemoryThirdAccountBindingStore implements ThirdAccountBindingStore {

    private final Map<Long, ThirdAccountBinding> bindings = new ConcurrentHashMap<>();

    private final AtomicLong idGenerator = new AtomicLong();

    @Override
    public ThirdAccountBinding save(ThirdAccountBinding binding) {
        if (binding.getId() == null) {
            binding.setId(idGenerator.incrementAndGet());
        }
        bindings.put(binding.getId(), binding);
        return binding;
    }

    @Override
    public Optional<ThirdAccountBinding> findActive(ThirdPartyPlatform platform, String openId) {
        return bindings.values().stream()
                .filter(ThirdAccountBinding::active)
                .filter(binding -> binding.getPlatform() == platform && binding.getOpenId().equals(openId))
                .findFirst();
    }

    @Override
    public Optional<ThirdAccountBinding> findByUnionId(ThirdPartyPlatform platform, String unionId) {
        if (unionId == null || unionId.isBlank()) {
            return Optional.empty();
        }
        return bindings.values().stream()
                .filter(ThirdAccountBinding::active)
                .filter(binding -> binding.getPlatform() == platform && unionId.equals(binding.getUnionId()))
                .findFirst();
    }

    @Override
    public List<ThirdAccountBinding> listByUser(String userId) {
        return bindings.values().stream()
                .filter(ThirdAccountBinding::active)
                .filter(binding -> binding.getUserId().equals(userId))
                .sorted(Comparator.comparingLong(ThirdAccountBinding::getBoundAt))
                .toList();
    }

    @Override
    public List<ThirdAccountBinding> listAll() {
        return bindings.values().stream()
                .sorted(Comparator.comparingLong(ThirdAccountBinding::getBoundAt))
                .toList();
    }
}
