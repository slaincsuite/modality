package cn.slainc.utils.validation;

import jakarta.validation.Validator;
import jakarta.validation.Validation;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ValidationUtilsTest {

    static class Form {
        @NotBlank
        private String name;
        @Min(18)
        private int age;

        Form(String name, int age) {
            this.name = name;
            this.age = age;
        }
    }

    interface AdultGroup {
    }

    @BeforeAll
    static void setUp() {
        Validation.buildDefaultValidatorFactory();
    }

    private Validator validator() {
        return Validation.buildDefaultValidatorFactory().getValidator();
    }

    @Test
    void validTargetProducesNoViolations() {
        assertTrue(ValidationUtils.isValid(validator(), new Form("bob", 20)));
        assertEquals(0, ValidationUtils.validate(validator(), new Form("bob", 20)).size());
    }

    @Test
    void collectsViolations() {
        Form form = new Form("", 10);
        assertEquals(2, ValidationUtils.validate(validator(), form).size());
        List<String> messages = ValidationUtils.validateMessages(validator(), form);
        assertEquals(2, messages.size());
        Map<String, String> mapped = ValidationUtils.validateToMap(validator(), form);
        assertTrue(mapped.containsKey("name"));
        assertTrue(mapped.containsKey("age"));
    }

    @Test
    void throwsOnInvalid() {
        assertThrows(IllegalArgumentException.class,
                () -> ValidationUtils.validateOrThrow(validator(), new Form("", 10)));
        ValidationUtils.validateOrThrow(validator(), new Form("bob", 20));
    }

    @Test
    void toleratesMissingArguments() {
        assertTrue(ValidationUtils.validate(null, new Form("bob", 20)).isEmpty());
        assertTrue(ValidationUtils.validate(validator(), null).isEmpty());
        assertFalse(ValidationUtils.isValid(validator(), new Form(null, 1)));
    }
}
