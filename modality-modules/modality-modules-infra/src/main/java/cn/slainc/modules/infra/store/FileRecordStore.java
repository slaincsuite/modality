package cn.slainc.modules.infra.store;

import cn.slainc.core.jdbc.JdbcPager;
import cn.slainc.modules.infra.InfraKeys;
import java.util.ArrayList;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;

public class FileRecordStore {

    public record FileView(long id, String fileKey, String name, String folder, String contentType, String ext,
                           long size, String backend, String md5, Long uploaderId, String uploaderName,
                           Long tenantId, String status, long createTime, long updateTime) {
    }

    private final JdbcTemplate jdbc;

    public FileRecordStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void insert(FileView view) {
        jdbc.update("INSERT INTO " + InfraKeys.FILE_RECORD_TABLE
                        + " (file_key, name, folder, content_type, ext, size, backend, md5, uploader_id,"
                        + " uploader_name, tenant_id, status, create_time, update_time)"
                        + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                view.fileKey(), view.name(), view.folder(), view.contentType(), view.ext(), view.size(),
                view.backend(), view.md5(), view.uploaderId(), view.uploaderName(), view.tenantId(), view.status(),
                view.createTime(), view.updateTime());
    }

    public FileView find(Long id) {
        List<FileView> rows = jdbc.query(selectSql() + " WHERE id = ?", mapper(), id);
        return rows.isEmpty() ? null : rows.get(0);
    }

    public FileView findByKey(String fileKey) {
        List<FileView> rows = jdbc.query(selectSql() + " WHERE file_key = ?", mapper(), fileKey);
        return rows.isEmpty() ? null : rows.get(0);
    }

    public cn.slainc.core.result.PageResult<FileView> page(long current, long size, String keyword, String folder,
                                                            String contentType, Long uploaderId, Long from, Long to) {
        StringBuilder where = new StringBuilder(" WHERE 1 = 1");
        List<Object> args = new ArrayList<>();
        if (keyword != null && !keyword.isBlank()) {
            where.append(" AND (name LIKE ? OR file_key LIKE ?)");
            String like = "%" + keyword.trim() + "%";
            args.add(like);
            args.add(like);
        }
        if (folder != null && !folder.isBlank()) {
            where.append(" AND folder = ?");
            args.add(folder.trim());
        }
        if (contentType != null && !contentType.isBlank()) {
            where.append(" AND content_type LIKE ?");
            args.add(contentType.trim() + "%");
        }
        if (uploaderId != null) {
            where.append(" AND uploader_id = ?");
            args.add(uploaderId);
        }
        if (from != null) {
            where.append(" AND create_time >= ?");
            args.add(from);
        }
        if (to != null) {
            where.append(" AND create_time <= ?");
            args.add(to);
        }
        return JdbcPager.page(jdbc,
                "SELECT COUNT(*) FROM " + InfraKeys.FILE_RECORD_TABLE + where,
                selectSql() + where + " ORDER BY id DESC",
                args, current, size, 500, mapper());
    }

    public int updateStatus(long id, String status) {
        return jdbc.update("UPDATE " + InfraKeys.FILE_RECORD_TABLE
                + " SET status = ?, update_time = ? WHERE id = ?", status, System.currentTimeMillis(), id);
    }

    public int updateNameOrFolder(long id, String name, String folder) {
        return jdbc.update("UPDATE " + InfraKeys.FILE_RECORD_TABLE
                        + " SET name = COALESCE(?, name), folder = COALESCE(?, folder), update_time = ? WHERE id = ?",
                name, folder, System.currentTimeMillis(), id);
    }

    public int delete(long id) {
        return jdbc.update("DELETE FROM " + InfraKeys.FILE_RECORD_TABLE + " WHERE id = ?", id);
    }

    private static String selectSql() {
        return "SELECT id, file_key, name, folder, content_type, ext, size, backend, md5, uploader_id,"
                + " uploader_name, tenant_id, status, create_time, update_time FROM " + InfraKeys.FILE_RECORD_TABLE;
    }

    private static org.springframework.jdbc.core.RowMapper<FileView> mapper() {
        return (rs, rowNum) -> new FileView(rs.getLong("id"), rs.getString("file_key"), rs.getString("name"),
                rs.getString("folder"), rs.getString("content_type"), rs.getString("ext"), rs.getLong("size"),
                rs.getString("backend"), rs.getString("md5"),
                rs.getObject("uploader_id") == null ? null : rs.getLong("uploader_id"), rs.getString("uploader_name"),
                rs.getObject("tenant_id") == null ? null : rs.getLong("tenant_id"), rs.getString("status"),
                rs.getLong("create_time"), rs.getLong("update_time"));
    }
}
