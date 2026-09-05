package cn.slainc.sensitive.jackson;

import cn.slainc.sensitive.annotation.Sensitive;
import cn.slainc.sensitive.core.SensitiveConfig;
import cn.slainc.sensitive.core.SensitiveMasker;
import cn.slainc.sensitive.core.SensitiveRule;
import tools.jackson.databind.BeanDescription;
import tools.jackson.databind.JavaType;
import tools.jackson.databind.SerializationConfig;
import tools.jackson.databind.ValueSerializer;
import tools.jackson.databind.introspect.AnnotatedMember;
import tools.jackson.databind.introspect.BeanPropertyDefinition;
import tools.jackson.databind.ser.BeanPropertyWriter;
import tools.jackson.databind.ser.ValueSerializerModifier;
import tools.jackson.databind.ser.bean.BeanSerializerBase;
import tools.jackson.databind.type.ArrayType;
import tools.jackson.databind.type.CollectionLikeType;
import tools.jackson.databind.type.CollectionType;
import tools.jackson.databind.type.MapLikeType;
import tools.jackson.databind.type.MapType;

import java.util.List;

public class SensitiveSerializerModifier extends ValueSerializerModifier {

    private final SensitiveMasker masker;

    private final SensitiveConfig config;

    public SensitiveSerializerModifier(SensitiveMasker masker, SensitiveConfig config) {
        this.masker = masker;
        this.config = config;
    }

    @Override
    public List<BeanPropertyWriter> changeProperties(SerializationConfig serializationConfig,
                                                     BeanDescription.Supplier beanDescSupplier,
                                                     List<BeanPropertyWriter> properties) {
        BeanDescription beanDesc = beanDescSupplier.get();
        Sensitive classLevel = beanDesc.getClassInfo().getAnnotation(Sensitive.class);
        List<BeanPropertyDefinition> definitions = beanDesc.findProperties();
        for (BeanPropertyWriter writer : properties) {
            Sensitive annotation = findAnnotation(definitions, writer);
            if (annotation == null) {
                if (classLevel == null || !isTextual(writer.getType())) {
                    continue;
                }
                annotation = classLevel;
            }
            writer.assignSerializer(propertySerializer(writer.getType(), masker.rule(annotation)));
        }
        return properties;
    }

    @Override
    public ValueSerializer<?> modifySerializer(SerializationConfig serializationConfig,
                                               BeanDescription.Supplier beanDescSupplier, ValueSerializer<?> serializer) {
        return serializer instanceof BeanSerializerBase ? guard(serializer) : serializer;
    }

    @Override
    public ValueSerializer<?> modifyArraySerializer(SerializationConfig serializationConfig, ArrayType type,
                                                    BeanDescription.Supplier beanDescSupplier, ValueSerializer<?> serializer) {
        return guard(serializer);
    }

    @Override
    public ValueSerializer<?> modifyCollectionSerializer(SerializationConfig serializationConfig, CollectionType type,
                                                         BeanDescription.Supplier beanDescSupplier, ValueSerializer<?> serializer) {
        return guard(serializer);
    }

    @Override
    public ValueSerializer<?> modifyCollectionLikeSerializer(SerializationConfig serializationConfig, CollectionLikeType type,
                                                             BeanDescription.Supplier beanDescSupplier, ValueSerializer<?> serializer) {
        return guard(serializer);
    }

    @Override
    public ValueSerializer<?> modifyMapSerializer(SerializationConfig serializationConfig, MapType type,
                                                  BeanDescription.Supplier beanDescSupplier, ValueSerializer<?> serializer) {
        return guard(serializer);
    }

    @Override
    public ValueSerializer<?> modifyMapLikeSerializer(SerializationConfig serializationConfig, MapLikeType type,
                                                      BeanDescription.Supplier beanDescSupplier, ValueSerializer<?> serializer) {
        return guard(serializer);
    }

    private ValueSerializer<?> guard(ValueSerializer<?> serializer) {
        if (!config.cycleSafe() || serializer == null || serializer instanceof CycleGuardSerializer) {
            return serializer;
        }
        return new CycleGuardSerializer<>(serializer);
    }

    private Sensitive findAnnotation(List<BeanPropertyDefinition> definitions, BeanPropertyWriter writer) {
        AnnotatedMember member = writer.getMember();
        for (BeanPropertyDefinition definition : definitions) {
            if (definition.getPrimaryMember() != member && !definition.getInternalName().equals(writer.getName())) {
                continue;
            }
            Sensitive annotation = annotation(definition.getGetter());
            if (annotation == null) {
                annotation = annotation(definition.getField());
            }
            if (annotation == null) {
                annotation = annotation(definition.getSetter());
            }
            if (annotation == null) {
                annotation = annotation(definition.getConstructorParameter());
            }
            if (annotation != null) {
                return annotation;
            }
        }
        return writer.getAnnotation(Sensitive.class);
    }

    private Sensitive annotation(AnnotatedMember member) {
        return member == null ? null : member.getAnnotation(Sensitive.class);
    }

    private boolean isTextual(JavaType type) {
        return type != null && CharSequence.class.isAssignableFrom(type.getRawClass());
    }

    @SuppressWarnings("unchecked")
    private ValueSerializer<Object> propertySerializer(JavaType type, SensitiveRule rule) {
        if (type.isMapLikeType()) {
            return (ValueSerializer<Object>) (ValueSerializer<?>) new SensitiveMapSerializer(masker, rule);
        }
        if (type.isCollectionLikeType() || type.isArrayType()) {
            return (ValueSerializer<Object>) (ValueSerializer<?>) new SensitiveCollectionSerializer(masker, rule);
        }
        return (ValueSerializer<Object>) (ValueSerializer<?>) new SensitiveValueSerializer(masker, rule);
    }
}
