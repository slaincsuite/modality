package cn.slainc.utils.reflect;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReflectUtilsTest {

    enum Level {
        LOW, HIGH
    }

    static class Parent {
        private String inherited = "p";

        String inherited() {
            return inherited;
        }
    }

    static class Sample extends Parent {
        private String name;
        private int age;
        private List<String> tags = new ArrayList<>();

        public Sample() {
        }

        public Sample(String name) {
            this.name = name;
        }

        @Override
        public String toString() {
            return name;
        }

        public String greet(String who) {
            return "hi " + who;
        }

        private String secret() {
            return "s";
        }
    }

    @Test
    void fieldReadWrite() {
        Sample sample = new Sample();
        ReflectUtils.setFieldValue(sample, "name", "abc");
        assertEquals("abc", ReflectUtils.<String>getFieldValue(sample, "name"));
        ReflectUtils.setFieldValue(sample, "age", "42");
        assertEquals(42, (int) ReflectUtils.<Integer>getFieldValue(sample, "age"));
        assertNotNull(ReflectUtils.getField(Sample.class, "inherited"));
        assertThrows(IllegalArgumentException.class, () -> ReflectUtils.getField(Sample.class, "missing"));
        assertTrue(ReflectUtils.getAllFields(Sample.class).size() >= 4);
        assertTrue(ReflectUtils.getAllMethods(Sample.class).size() >= 4);
    }

    @Test
    void methodInvocation() {
        Sample sample = new Sample();
        assertEquals("hi bob", ReflectUtils.invokeMethod(sample, "greet", "bob"));
        assertEquals("s", ReflectUtils.invokeMethod(sample, "secret"));
        assertThrows(IllegalArgumentException.class, () -> ReflectUtils.invokeMethod(sample, "missing"));
        assertThrows(IllegalArgumentException.class, () -> ReflectUtils.invokeMethod(null, "greet"));
    }

    @Test
    void instantiation() {
        Sample sample = ReflectUtils.newInstance(Sample.class);
        assertNotNull(sample);
        assertEquals("[]", sample.tags.toString());
    }

    @Test
    void genericsInspection() {
        Field tags = ReflectUtils.getField(Sample.class, "tags");
        assertEquals(1, ReflectUtils.getGenericArguments(tags).length);
        assertEquals(String.class, ReflectUtils.getGenericArguments(tags)[0]);
        assertEquals(List.class, ReflectUtils.getRawType(tags));
        Field name = ReflectUtils.getField(Sample.class, "name");
        assertEquals(0, ReflectUtils.getGenericArguments(name).length);
        assertEquals(String.class, ReflectUtils.getRawType(name));
    }

    @Test
    void typeConversion() {
        assertEquals(42, (int) ReflectUtils.convert("42", Integer.class));
        assertEquals("42", ReflectUtils.convert(42, String.class));
        assertEquals(42L, (long) ReflectUtils.convert("42", Long.class));
        assertEquals(new BigDecimal("1.5"), ReflectUtils.convert("1.5", BigDecimal.class));
        assertEquals(Level.HIGH, ReflectUtils.convert("HIGH", Level.class));
        assertEquals(LocalDate.of(2024, 1, 1), ReflectUtils.convert("2024-01-01", LocalDate.class));
        assertNull(ReflectUtils.convert(null, String.class));
        assertEquals("abc", ReflectUtils.convert("abc", String.class));
    }

    @Test
    void proxyDetection() {
        assertTrue(ReflectUtils.isProxy(java.util.List.class) == false);
        assertTrue(java.lang.reflect.Proxy.isProxyClass(
                java.lang.reflect.Proxy.newProxyInstance(
                        Runnable.class.getClassLoader(), new Class<?>[]{Runnable.class}, (p, m, a) -> null).getClass()));
        assertNotNull(ReflectUtils.getTargetClass(new Sample()));
        assertNull(ReflectUtils.getTargetClass(null));
    }
}
