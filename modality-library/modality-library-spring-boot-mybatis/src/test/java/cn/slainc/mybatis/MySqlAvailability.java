package cn.slainc.mybatis;

import java.sql.Connection;
import java.sql.DriverManager;

public final class MySqlAvailability {

    public static final String URL = "jdbc:mysql://127.0.0.1:3306/modality"
            + "?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true&useSSL=false";

    public static final String USER = "omni";

    public static final String PASSWORD = "omni-pass";

    private static volatile Boolean available;

    private MySqlAvailability() {
    }

    public static boolean available() {
        Boolean cached = available;
        if (cached != null) {
            return cached;
        }
        boolean result = probe();
        available = result;
        return result;
    }

    private static boolean probe() {
        try (Connection connection = DriverManager.getConnection(URL, USER, PASSWORD)) {
            return connection.isValid(2);
        } catch (Exception exception) {
            return false;
        }
    }
}
