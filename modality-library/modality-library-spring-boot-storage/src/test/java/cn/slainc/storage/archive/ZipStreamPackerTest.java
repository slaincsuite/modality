package cn.slainc.storage.archive;

import cn.slainc.storage.local.LocalFileStorage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.assertj.core.api.Assertions.assertThat;

class ZipStreamPackerTest {

    @TempDir
    Path root;

    @Test
    @DisplayName("打包 N 个文件后可解压且内容一致")
    void packAndUnzip() throws IOException {
        LocalFileStorage storage = new LocalFileStorage("local", root, null);
        Map<String, byte[]> expected = new LinkedHashMap<>();
        for (int index = 0; index < 5; index++) {
            byte[] data = ("文件内容-" + index).getBytes(StandardCharsets.UTF_8);
            String key = "docs/" + index + ".txt";
            storage.upload(key, new ByteArrayInputStream(data), data.length, "text/plain");
            expected.put(index + ".txt", data);
        }

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        long written = ZipStreamPacker.pack(storage, expected.keySet().stream()
                .map(name -> "docs/" + name).toList(), output);
        assertThat(written).isEqualTo(output.size());
        assertThat(written).isGreaterThan(0L);

        Map<String, byte[]> actual = unzip(output.toByteArray());
        assertThat(actual.keySet()).containsExactlyElementsOf(expected.keySet());
        for (Map.Entry<String, byte[]> entry : expected.entrySet()) {
            assertThat(actual.get(entry.getKey())).isEqualTo(entry.getValue());
        }
    }

    @Test
    @DisplayName("大文件流式打包不会一次性读入内存：8MB 内容打包后大小接近压缩结果")
    void largeFileStreamed() throws IOException {
        LocalFileStorage storage = new LocalFileStorage("local", root, null);
        byte[] data = new byte[8 * 1024 * 1024];
        new Random(3).nextBytes(data);
        storage.upload("big.bin", new ByteArrayInputStream(data), data.length, "application/octet-stream");

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ZipStreamPacker.pack(storage, List.of("big.bin"), output);
        Map<String, byte[]> actual = unzip(output.toByteArray());
        assertThat(actual.get("big.bin")).isEqualTo(data);
    }

    @Test
    @DisplayName("不同目录的同名文件自动去重，打包不再整批失败")
    void duplicateEntryNamesAreRenamed() throws IOException {
        LocalFileStorage storage = new LocalFileStorage("local", root, null);
        byte[] first = "目录A内容".getBytes(StandardCharsets.UTF_8);
        byte[] second = "目录B内容".getBytes(StandardCharsets.UTF_8);
        storage.upload("dir-a/report.txt", new ByteArrayInputStream(first), first.length, "text/plain");
        storage.upload("dir-b/report.txt", new ByteArrayInputStream(second), second.length, "text/plain");

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ZipStreamPacker.pack(storage, List.of("dir-a/report.txt", "dir-b/report.txt"), output);

        Map<String, byte[]> actual = unzip(output.toByteArray());
        assertThat(actual).hasSize(2);
        assertThat(actual.get("report.txt")).isEqualTo(first);
        assertThat(actual.get("report(1).txt")).isEqualTo(second);
    }

    @Test
    @DisplayName("打包条目名剥离目录，避免 ZIP 内路径穿越")
    void entryNameIsSanitized() {
        assertThat(ZipStreamPacker.entryName("../../etc/passwd")).isEqualTo("passwd");
        assertThat(ZipStreamPacker.entryName("a/b/c.txt")).isEqualTo("c.txt");
    }

    private static Map<String, byte[]> unzip(byte[] archive) throws IOException {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(archive))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                entries.put(entry.getName(), zip.readAllBytes());
                zip.closeEntry();
            }
        }
        return entries;
    }
}
