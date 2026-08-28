package cn.slainc.idempotent.support;

import cn.slainc.idempotent.annotation.Idempotent;
import cn.slainc.idempotent.store.IdempotentStore;

import java.lang.reflect.Method;

public record IdempotentContext(
        Method method,
        Object[] args,
        Object target,
        Idempotent annotation,
        String methodId,
        String requestPath,
        Long userId,
        String keyValue,
        String token,
        IdempotentSettings settings,
        IdempotentStore store) {

    public IdempotentContext withRuntime(IdempotentSettings runtimeSettings, IdempotentStore runtimeStore) {
        return new IdempotentContext(method, args, target, annotation, methodId, requestPath, userId, keyValue,
                token, runtimeSettings, runtimeStore);
    }

    public boolean hasToken() {
        return token != null && !token.isBlank();
    }
}
