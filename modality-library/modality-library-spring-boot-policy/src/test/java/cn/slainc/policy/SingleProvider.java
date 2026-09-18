package cn.slainc.policy;

import org.springframework.beans.factory.ObjectProvider;

import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;

public class SingleProvider<T> implements ObjectProvider<T> {

    private final T value;

    public SingleProvider(T value) {
        this.value = value;
    }

    public static <T> SingleProvider<T> of(T value) {
        return new SingleProvider<>(value);
    }

    public static <T> SingleProvider<T> empty() {
        return new SingleProvider<>(null);
    }

    @Override
    public T getObject(Object... args) {
        return getObject();
    }

    @Override
    public T getObject() {
        if (value == null) {
            throw new NoSuchElementException("没有可用的 Bean");
        }
        return value;
    }

    @Override
    public T getIfAvailable() {
        return value;
    }

    @Override
    public Iterator<T> iterator() {
        return value == null ? Collections.emptyIterator() : List.of(value).iterator();
    }
}
