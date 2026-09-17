package cn.slainc.sign;

import org.springframework.beans.factory.ObjectProvider;

import java.util.Iterator;
import java.util.NoSuchElementException;

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
        return value == null ? java.util.Collections.emptyIterator() : java.util.List.of(value).iterator();
    }
}
