package cn.slainc.core.enums;

final class DictEnums {

    private DictEnums() {
    }

    static <E extends Enum<E> & DictEnum> E of(Class<E> type, Object value) {
        if (value == null) {
            return null;
        }
        for (E candidate : type.getEnumConstants()) {
            if (matches(candidate.value(), value)) {
                return candidate;
            }
        }
        return null;
    }

    private static boolean matches(Object candidateValue, Object value) {
        if (candidateValue instanceof String left && value instanceof String right) {
            return left.equalsIgnoreCase(right);
        }
        return candidateValue.equals(value);
    }
}
