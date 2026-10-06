package cn.slainc.web.http;

import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

public final class DownloadResponses {

    public static final String CONTENT_TYPE_EXCEL =
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    public static final String CONTENT_TYPE_JSON = "application/json";

    private DownloadResponses() {
    }

    public static void writeJson(HttpServletResponse response, byte[] content) {
        response.setContentType(CONTENT_TYPE_JSON);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        write(response, content);
    }

    public static void writeAttachment(HttpServletResponse response, String filename, String contentType,
            byte[] content) {
        attachmentHeaders(response, filename, contentType);
        write(response, content);
    }

    public static OutputStream attachmentStream(HttpServletResponse response, String filename, String contentType) {
        attachmentHeaders(response, filename, contentType);
        try {
            return response.getOutputStream();
        } catch (IOException exception) {
            throw new UncheckedIOException("写出下载内容失败: " + filename, exception);
        }
    }

    private static void attachmentHeaders(HttpServletResponse response, String filename, String contentType) {
        response.setContentType(contentType);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setHeader("Content-Disposition", "attachment; filename=" + filename);
    }

    private static void write(HttpServletResponse response, byte[] content) {
        try {
            response.getOutputStream().write(content);
        } catch (IOException exception) {
            throw new UncheckedIOException("写出下载内容失败", exception);
        }
    }
}
