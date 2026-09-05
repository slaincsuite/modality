package cn.slainc.sensitive.core;

import cn.slainc.sensitive.annotation.Sensitive;
import cn.slainc.sensitive.annotation.SensitiveStrategy;
import cn.slainc.sensitive.annotation.SensitiveType;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;

public record SensitiveRule(SensitiveType type, SensitiveStrategy strategy, int prefixKeep, int suffixKeep,
                            char maskChar, String replacement, Set<String> groups) {

    public SensitiveRule {
        groups = groups == null ? Set.of() : Set.copyOf(groups);
    }

    public static SensitiveRule of(Sensitive annotation, SensitiveConfig config) {
        SensitiveType type = annotation.type();
        int prefixKeep = annotation.prefixKeep() >= 0 ? annotation.prefixKeep() : type.prefixKeep();
        int suffixKeep = annotation.suffixKeep() >= 0 ? annotation.suffixKeep() : type.suffixKeep();
        char maskChar = annotation.maskChar() == 0 ? config.maskChar() : annotation.maskChar();
        String replacement = annotation.replacement().isEmpty() ? config.replacement() : annotation.replacement();
        Set<String> groups = new LinkedHashSet<>(Arrays.asList(annotation.groups()));
        return new SensitiveRule(type, annotation.strategy(), prefixKeep, suffixKeep, maskChar, replacement, groups);
    }

    public boolean grouped() {
        return !groups.isEmpty();
    }
}
