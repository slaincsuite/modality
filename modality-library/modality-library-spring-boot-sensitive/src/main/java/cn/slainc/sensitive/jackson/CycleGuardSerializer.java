package cn.slainc.sensitive.jackson;

import com.fasterxml.jackson.annotation.JsonFormat;
import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.BeanProperty;
import tools.jackson.databind.JavaType;
import tools.jackson.databind.SerializationConfig;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ValueSerializer;
import tools.jackson.databind.jsonFormatVisitors.JsonFormatVisitorWrapper;
import tools.jackson.databind.jsontype.TypeSerializer;
import tools.jackson.databind.ser.PropertyWriter;
import tools.jackson.databind.util.NameTransformer;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.Set;

public class CycleGuardSerializer<T> extends ValueSerializer<T> {

    private static final ThreadLocal<Set<Object>> ANCESTORS = ThreadLocal.withInitial(
            () -> Collections.newSetFromMap(new IdentityHashMap<>()));

    private final ValueSerializer<T> delegate;

    public CycleGuardSerializer(ValueSerializer<T> delegate) {
        this.delegate = delegate;
    }

    // 返回 true 是为了让 BeanPropertyWriter 跳过 "Direct self-reference" 的失败分支，改由本类写出 null
    @Override
    public boolean usesObjectId() {
        return true;
    }

    @Override
    public void serialize(T value, JsonGenerator generator, SerializationContext context) throws JacksonException {
        Set<Object> ancestors = ANCESTORS.get();
        if (!ancestors.add(value)) {
            generator.writeNull();
            return;
        }
        try {
            delegate.serialize(value, generator, context);
        } finally {
            ancestors.remove(value);
            if (ancestors.isEmpty()) {
                ANCESTORS.remove();
            }
        }
    }

    @Override
    public void serializeWithType(T value, JsonGenerator generator, SerializationContext context, TypeSerializer typeSerializer)
            throws JacksonException {
        Set<Object> ancestors = ANCESTORS.get();
        if (!ancestors.add(value)) {
            generator.writeNull();
            return;
        }
        try {
            delegate.serializeWithType(value, generator, context, typeSerializer);
        } finally {
            ancestors.remove(value);
            if (ancestors.isEmpty()) {
                ANCESTORS.remove();
            }
        }
    }

    @Override
    public void resolve(SerializationContext context) {
        delegate.resolve(context);
    }

    @Override
    public ValueSerializer<?> createContextual(SerializationContext context, BeanProperty property) {
        ValueSerializer<?> contextual = delegate.createContextual(context, property);
        return contextual == delegate ? this : new CycleGuardSerializer<>(contextual);
    }

    @Override
    public ValueSerializer<T> unwrappingSerializer(NameTransformer transformer) {
        ValueSerializer<T> unwrapped = delegate.unwrappingSerializer(transformer);
        return unwrapped == delegate ? this : new CycleGuardSerializer<>(unwrapped);
    }

    @Override
    public ValueSerializer<T> replaceDelegatee(ValueSerializer<?> delegatee) {
        return delegate.replaceDelegatee(delegatee);
    }

    @Override
    public ValueSerializer<?> withFilterId(Object filterId) {
        ValueSerializer<?> filtered = delegate.withFilterId(filterId);
        return filtered == delegate ? this : new CycleGuardSerializer<>(filtered);
    }

    @Override
    public ValueSerializer<?> withIgnoredProperties(Set<String> properties) {
        ValueSerializer<?> filtered = delegate.withIgnoredProperties(properties);
        return filtered == delegate ? this : new CycleGuardSerializer<>(filtered);
    }

    @Override
    public ValueSerializer<?> withFormatOverrides(SerializationConfig config, JsonFormat.Value formatOverrides) {
        ValueSerializer<?> overridden = delegate.withFormatOverrides(config, formatOverrides);
        return overridden == delegate ? this : new CycleGuardSerializer<>(overridden);
    }

    @Override
    public ValueSerializer<?> getDelegatee() {
        return delegate;
    }

    @Override
    public Class<?> handledType() {
        return delegate.handledType();
    }

    @Override
    public boolean isUnwrappingSerializer() {
        return delegate.isUnwrappingSerializer();
    }

    @Override
    public Iterator<PropertyWriter> properties() {
        return delegate.properties();
    }

    @Override
    public boolean isEmpty(SerializationContext context, T value) {
        return delegate.isEmpty(context, value);
    }

    @Override
    public void acceptJsonFormatVisitor(JsonFormatVisitorWrapper visitor, JavaType type) {
        delegate.acceptJsonFormatVisitor(visitor, type);
    }
}
