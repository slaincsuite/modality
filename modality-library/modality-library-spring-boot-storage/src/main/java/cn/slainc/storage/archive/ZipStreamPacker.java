package cn.slainc.storage.archive;

import cn.slainc.storage.FileStorage;
import cn.slainc.storage.StorageException;

import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.zip.Deflater;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public final class ZipStreamPacker {

    private static final int BUFFER = 8192;

    private ZipStreamPacker() {
    }

    public static long pack(OutputStream target, List<ZipEntrySource> sources) {
        CountingOutputStream counting = new CountingOutputStream(target);
        ZipOutputStream zip = new ZipOutputStream(counting);
        zip.setLevel(Deflater.DEFAULT_COMPRESSION);
        try {
            Set<String> used = new HashSet<>();
            for (ZipEntrySource source : sources) {
                addEntry(zip, source, used);
            }
            zip.finish();
            zip.flush();
        } catch (IOException ex) {
            throw new StorageException("流式打包 ZIP 失败", ex);
        }
        return counting.count();
    }

    public static long pack(FileStorage storage, Collection<String> keys, OutputStream target) {
        List<ZipEntrySource> sources = keys.stream().<ZipEntrySource>map(key -> new ZipEntrySource() {
            @Override
            public String name() {
                return entryName(key);
            }

            @Override
            public InputStream open() {
                return storage.download(key);
            }
        }).toList();
        return pack(target, sources);
    }

    public static String entryName(String key) {
        if (key == null || key.isBlank()) {
            throw new StorageException("打包条目标名不能为空");
        }
        String normalized = key.replace('\\', '/');
        int index = normalized.lastIndexOf('/');
        String name = index < 0 ? normalized : normalized.substring(index + 1);
        if (name.isBlank() || "..".equals(name)) {
            throw new StorageException("非法的打包条目标名：" + key);
        }
        return name;
    }

    private static void addEntry(ZipOutputStream zip, ZipEntrySource source, Set<String> used) throws IOException {
        ZipEntry entry = new ZipEntry(uniqueName(entryName(source.name()), used));
        zip.putNextEntry(entry);
        try (InputStream input = source.open()) {
            byte[] buffer = new byte[BUFFER];
            int read;
            while ((read = input.read(buffer)) >= 0) {
                zip.write(buffer, 0, read);
            }
        }
        zip.closeEntry();
    }

    // 不同目录的同名文件会撞条目名并抛 ZipException，这里按出现顺序追加序号
    private static String uniqueName(String name, Set<String> used) {
        if (used.add(name)) {
            return name;
        }
        int dot = name.lastIndexOf('.');
        String base = dot <= 0 ? name : name.substring(0, dot);
        String suffix = dot <= 0 ? "" : name.substring(dot);
        for (int index = 1; ; index++) {
            String candidate = base + "(" + index + ")" + suffix;
            if (used.add(candidate)) {
                return candidate;
            }
        }
    }

    private static final class CountingOutputStream extends FilterOutputStream {

        private long count;

        private CountingOutputStream(OutputStream out) {
            super(out);
        }

        @Override
        public void write(int value) throws IOException {
            out.write(value);
            count++;
        }

        @Override
        public void write(byte[] data, int offset, int length) throws IOException {
            out.write(data, offset, length);
            count += length;
        }

        private long count() {
            return count;
        }
    }
}
