package cn.slainc.idempotent.support;

import org.springframework.beans.factory.ObjectProvider;

import java.util.Iterator;
import java.util.List;

public final class TestObjectProviders {

    private TestObjectProviders() {
    }

    public static <T> ObjectProvider<T> of(T instance) {
        return new FixedObjectProvider<>(instance);
    }

    public static <T> ObjectProvider<T> empty() {
        return new FixedObjectProvider<>(null);
    }

    private static final class FixedObjectProvider<T> implements ObjectProvider<T> {

        private final T instance;

        private FixedObjectProvider(T instance) {
            this.instance = instance;
        }

        @Override
        public T getObject() {
            return instance;
        }

        @Override
        public T getIfAvailable() {
            return instance;
        }

        @Override
        public Iterator<T> iterator() {
            return instance == null ? List.<T>of().iterator() : List.of(instance).iterator();
        }
    }
}
