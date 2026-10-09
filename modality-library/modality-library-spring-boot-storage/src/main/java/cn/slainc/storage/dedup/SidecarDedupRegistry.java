package cn.slainc.storage.dedup;

import cn.slainc.storage.StorageException;
import cn.slainc.utils.digest.DigestUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.function.Supplier;

public class SidecarDedupRegistry implements DedupRegistry {

    private static final Logger log = LoggerFactory.getLogger(SidecarDedupRegistry.class);

    private static final String REF_SUFFIX = ".ref";

    private static final String KEY_SUFFIX = ".key";

    private final Path root;

    private final Path refsDirectory;

    private final Path keysDirectory;

    private final Path lockFile;

    private final Object monitor = new Object();

    public SidecarDedupRegistry(Path root) {
        this.root = root.toAbsolutePath().normalize();
        this.refsDirectory = this.root.resolve("refs");
        this.keysDirectory = this.root.resolve("keys");
        this.lockFile = this.root.resolve(".lock");
        try {
            Files.createDirectories(refsDirectory);
            Files.createDirectories(keysDirectory);
        } catch (IOException ex) {
            throw new StorageException("初始化秒传引用表目录失败：" + this.root, ex);
        }
    }

    public Path root() {
        return root;
    }

    @Override
    public Entry find(String id) {
        return id == null ? null : readRef(refFile(id), id);
    }

    @Override
    public String lookup(String logicalId) {
        if (logicalId == null) {
            return null;
        }
        Path file = keyFile(logicalId);
        try {
            return Files.isRegularFile(file) ? Files.readString(file, StandardCharsets.UTF_8).trim() : null;
        } catch (IOException ex) {
            log.warn("读取秒传逻辑键映射失败，按未命中处理：{}，原因：{}", file, ex.getMessage());
            return null;
        }
    }

    @Override
    public void bind(String logicalId, String id) {
        if (logicalId == null || id == null) {
            return;
        }
        locked(() -> {
            write(keyFile(logicalId), id);
            return null;
        });
    }

    @Override
    public String unbind(String logicalId) {
        if (logicalId == null) {
            return null;
        }
        return locked(() -> {
            String id = lookup(logicalId);
            if (id != null) {
                deleteQuietly(keyFile(logicalId));
            }
            return id;
        });
    }

    @Override
    public Entry register(String id, String key) {
        Path file = refFile(id);
        return locked(() -> {
            Entry existing = readRef(file, id);
            if (existing != null) {
                return existing;
            }
            write(file, key + "\n1\n");
            return new Entry(id, key, 1);
        });
    }

    @Override
    public Entry retain(String id) {
        Path file = refFile(id);
        return locked(() -> {
            Entry existing = readRef(file, id);
            if (existing == null) {
                return null;
            }
            Entry updated = new Entry(id, existing.key(), existing.references() + 1);
            write(file, updated.key() + "\n" + updated.references() + "\n");
            return updated;
        });
    }

    @Override
    public Entry release(String id) {
        Path file = refFile(id);
        return locked(() -> {
            Entry existing = readRef(file, id);
            if (existing == null) {
                return null;
            }
            int references = existing.references() - 1;
            if (references <= 0) {
                deleteQuietly(file);
                return new Entry(id, existing.key(), 0);
            }
            write(file, existing.key() + "\n" + references + "\n");
            return new Entry(id, existing.key(), references);
        });
    }

    @Override
    public void remove(String id) {
        if (id == null) {
            return;
        }
        locked(() -> {
            deleteQuietly(refFile(id));
            return null;
        });
    }

    private Entry readRef(Path file, String id) {
        try {
            if (!Files.isRegularFile(file)) {
                return null;
            }
            List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
            if (lines.size() < 2) {
                return null;
            }
            return new Entry(id, lines.get(0), Integer.parseInt(lines.get(1).trim()));
        } catch (IOException | RuntimeException ex) {
            log.warn("读取秒传引用记录失败，按未命中处理：{}，原因：{}", file, ex.getMessage());
            return null;
        }
    }

    private Path refFile(String id) {
        return refsDirectory.resolve(hash(id) + REF_SUFFIX);
    }

    private Path keyFile(String logicalId) {
        return keysDirectory.resolve(hash(logicalId) + KEY_SUFFIX);
    }

    private static String hash(String value) {
        return DigestUtils.md5(value.getBytes(StandardCharsets.UTF_8));
    }

    private <T> T locked(Supplier<T> action) {
        synchronized (monitor) {
            try (FileChannel channel = FileChannel.open(lockFile, StandardOpenOption.CREATE,
                    StandardOpenOption.READ, StandardOpenOption.WRITE)) {
                FileLock lock = null;
                try {
                    lock = channel.lock();
                } catch (OverlappingFileLockException ignored) {
                    log.debug("秒传引用表已在本进程内加锁，跳过文件锁");
                }
                try {
                    return action.get();
                } finally {
                    if (lock != null && lock.isValid()) {
                        lock.release();
                    }
                }
            } catch (IOException ex) {
                throw new StorageException("访问秒传引用表失败：" + lockFile, ex);
            }
        }
    }

    private void write(Path file, String content) {
        Path temp = null;
        try {
            temp = Files.createTempFile(file.getParent(), "dedup-", ".tmp");
            Files.writeString(temp, content, StandardCharsets.UTF_8);
            try {
                Files.move(temp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException ex) {
                Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException ex) {
            throw new StorageException("写入秒传引用表失败：" + file, ex);
        } finally {
            if (temp != null) {
                deleteQuietly(temp);
            }
        }
    }

    private static void deleteQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException ex) {
            log.debug("清理秒传引用表临时文件失败：{}，原因：{}", path, ex.getMessage());
        }
    }
}
