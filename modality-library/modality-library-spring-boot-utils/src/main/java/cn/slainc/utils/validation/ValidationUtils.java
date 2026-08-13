package cn.slainc.utils.validation;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class ValidationUtils {

    private ValidationUtils() {
    }

    public static <T> Set<ConstraintViolation<T>> validate(Validator validator, T target, Class<?>... groups) {
        if (validator == null || target == null) {
            return Set.of();
        }
        return validator.validate(target, groups);
    }

    public static <T> boolean isValid(Validator validator, T target, Class<?>... groups) {
        return validate(validator, target, groups).isEmpty();
    }

    public static <T> List<String> validateMessages(Validator validator, T target, Class<?>... groups) {
        List<String> messages = new ArrayList<>();
        for (ConstraintViolation<T> violation : validate(validator, target, groups)) {
            messages.add(violation.getPropertyPath() + ": " + violation.getMessage());
        }
        return messages;
    }

    public static <T> Map<String, String> validateToMap(Validator validator, T target, Class<?>... groups) {
        Map<String, String> result = new LinkedHashMap<>();
        for (ConstraintViolation<T> violation : validate(validator, target, groups)) {
            result.putIfAbsent(violation.getPropertyPath().toString(), violation.getMessage());
        }
        return result;
    }

    public static <T> void validateOrThrow(Validator validator, T target, Class<?>... groups) {
        Set<ConstraintViolation<T>> violations = validate(validator, target, groups);
        if (violations.isEmpty()) {
            return;
        }
        StringBuilder builder = new StringBuilder("参数校验失败: ");
        boolean first = true;
        for (ConstraintViolation<T> violation : violations) {
            if (!first) {
                builder.append("; ");
            }
            builder.append(violation.getPropertyPath()).append(' ').append(violation.getMessage());
            first = false;
        }
        throw new IllegalArgumentException(builder.toString());
    }
}
