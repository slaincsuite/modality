package cn.slainc.openapi;

import org.springframework.beans.factory.ObjectProvider;

import java.util.Collections;
import java.util.Iterator;
import java.util.List;

public class SingleProvider<T> implements ObjectProvider<T> {

    private final T value;

    public SingleProvider(T value) {
        this.value = value;
    }

    @Override
    public T getObject(Object... args) {
        return getObject();
    }

    @Override
    public T getObject() {
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
