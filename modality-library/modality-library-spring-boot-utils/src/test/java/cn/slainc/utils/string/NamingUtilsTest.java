package cn.slainc.utils.string;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NamingUtilsTest {

    @Test
    void toCamel() {
        assertEquals("userName", NamingUtils.toCamel("user_name"));
        assertEquals("userName", NamingUtils.toCamel("user-name"));
        assertEquals("userName", NamingUtils.toCamel("User Name"));
        assertEquals("httpServer", NamingUtils.toCamel("HTTPServer"));
    }

    @Test
    void toPascal() {
        assertEquals("UserName", NamingUtils.toPascal("user_name"));
        assertEquals("UserName", NamingUtils.toPascal("userName"));
    }

    @Test
    void toSnakeAndKebab() {
        assertEquals("user_name", NamingUtils.toSnake("userName"));
        assertEquals("user_name", NamingUtils.toSnake("UserName"));
        assertEquals("USER_NAME", NamingUtils.toUpperSnake("userName"));
        assertEquals("user-name", NamingUtils.toKebab("UserName"));
    }
}
