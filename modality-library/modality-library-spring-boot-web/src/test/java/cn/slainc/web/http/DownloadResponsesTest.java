package cn.slainc.web.http;

import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DownloadResponsesTest {

    @Test
    void writeJsonSetsContentTypeWithoutAttachment() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        DownloadResponses.writeJson(response, "{\"items\":[]}".getBytes(StandardCharsets.UTF_8));
        assertEquals("application/json", response.getContentType().split(";")[0]);
        assertEquals(StandardCharsets.UTF_8.name(), response.getCharacterEncoding());
        assertEquals(null, response.getHeader("Content-Disposition"));
        assertEquals("{\"items\":[]}", response.getContentAsString());
    }

    @Test
    void writeAttachmentSetsDispositionAndContent() {
        MockHttpServletResponse response = new MockHttpServletResponse();
        DownloadResponses.writeAttachment(response, "audit-logs.xlsx", DownloadResponses.CONTENT_TYPE_EXCEL,
                new byte[] {1, 2, 3});
        assertEquals(DownloadResponses.CONTENT_TYPE_EXCEL, response.getContentType().split(";")[0]);
        assertEquals("attachment; filename=audit-logs.xlsx", response.getHeader("Content-Disposition"));
        assertTrue(response.getContentAsByteArray().length == 3);
    }

    @Test
    void attachmentStreamWritesThroughToResponse() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        OutputStream stream = DownloadResponses.attachmentStream(response, "policies.xlsx",
                DownloadResponses.CONTENT_TYPE_EXCEL);
        stream.write(new byte[] {9, 8, 7});
        stream.flush();
        assertEquals("attachment; filename=policies.xlsx", response.getHeader("Content-Disposition"));
        assertEquals(3, response.getContentAsByteArray().length);
    }
}
