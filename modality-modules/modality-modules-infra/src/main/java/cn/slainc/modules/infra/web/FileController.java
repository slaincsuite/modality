package cn.slainc.modules.infra.web;

import cn.slainc.core.result.PageResult;
import cn.slainc.core.result.Result;
import cn.slainc.modules.infra.InfraKeys;
import cn.slainc.modules.infra.service.ChunkUploadService;
import cn.slainc.modules.infra.service.FileManagerService;
import cn.slainc.modules.infra.store.FileRecordStore;
import cn.slainc.modules.infra.service.FileUploadService;
import cn.slainc.modules.infra.service.PreviewGuard;
import cn.slainc.security.annotation.RequirePermission;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "文件存储", description = "文件上传下载与签名地址")
@RestController
@RequestMapping("/infra/file")
public class FileController {

    private final FileUploadService uploadService;

    private final FileManagerService managerService;

    private final ChunkUploadService chunkService;

    public FileController(FileUploadService uploadService, FileManagerService managerService,
                          ChunkUploadService chunkService) {
        this.uploadService = uploadService;
        this.managerService = managerService;
        this.chunkService = chunkService;
    }

    @RequirePermission(permissions = InfraKeys.PERMISSION_FILE_UPLOAD)
    @PostMapping("/upload")
    public Result<FileRecordStore.FileView> upload(@RequestParam("file") MultipartFile file,
                                                   @RequestParam(required = false) String folder,
                                                   @RequestParam(required = false) String md5) throws IOException {
        return Result.ok(uploadService.upload(file, folder, md5));
    }

    @RequirePermission(permissions = InfraKeys.PERMISSION_FILE_UPLOAD)
    @PostMapping("/upload/batch")
    public Result<List<FileUploadService.BatchResult>> uploadBatch(@RequestParam("files") MultipartFile[] files,
                                                                   @RequestParam(required = false) String folder)
            throws IOException {
        return Result.ok(uploadService.uploadBatch(files, folder));
    }

    @RequirePermission(permissions = InfraKeys.PERMISSION_FILE_UPLOAD)
    @PostMapping("/presign")
    public Result<FileUploadService.PresignView> presign(@RequestBody Map<String, Object> body) {
        long ttl = body.get("ttlMinutes") == null ? 15 : Long.parseLong(String.valueOf(body.get("ttlMinutes")));
        return Result.ok(uploadService.presignUpload(String.valueOf(body.get("filename")), ttl));
    }

    @RequirePermission(permissions = InfraKeys.PERMISSION_FILE_QUERY)
    @GetMapping("/stats/instant")
    public Result<Map<String, Object>> instantStats() {
        return Result.ok(uploadService.instantUploadStats());
    }

    @RequirePermission(permissions = InfraKeys.PERMISSION_FILE_QUERY)
    @GetMapping("/page")
    public Result<PageResult<FileRecordStore.FileView>> page(@RequestParam(defaultValue = "1") long current,
                                                              @RequestParam(defaultValue = "10") long size,
                                                              @RequestParam(required = false) String keyword,
                                                              @RequestParam(required = false) String folder,
                                                              @RequestParam(required = false) String contentType,
                                                              @RequestParam(required = false) Long uploaderId,
                                                              @RequestParam(required = false) Long from,
                                                              @RequestParam(required = false) Long to) {
        return Result.ok(managerService.page(current, size, keyword, folder, contentType, uploaderId, from, to));
    }

    @RequirePermission(permissions = InfraKeys.PERMISSION_FILE_QUERY)
    @GetMapping("/{id}")
    public Result<FileManagerService.DetailView> detail(@PathVariable long id) {
        return Result.ok(managerService.detail(id));
    }

    @RequirePermission(permissions = InfraKeys.PERMISSION_FILE_QUERY)
    @GetMapping("/{id}/download")
    public void download(@PathVariable long id, HttpServletResponse response) throws IOException {
        FileRecordStore.FileView view = managerService.find(id);
        response.setContentType(view.contentType() == null ? MediaType.APPLICATION_OCTET_STREAM_VALUE
                : view.contentType());
        response.setHeader("Content-Disposition", "attachment; filename*=UTF-8''"
                + managerService.encodeFilename(view.name()));
        response.setHeader("X-Content-Type-Options", PreviewGuard.CONTENT_TYPE_OPTIONS);
        managerService.download(id, response.getOutputStream());
    }

    @RequirePermission(permissions = InfraKeys.PERMISSION_FILE_QUERY)
    @PostMapping("/{id}/signed-url")
    public Result<FileManagerService.SignedUrlView> signedUrl(@PathVariable long id,
                                                              @RequestBody(required = false) Map<String, Object> body) {
        long ttl = body == null || body.get("ttlMinutes") == null ? 15
                : Long.parseLong(String.valueOf(body.get("ttlMinutes")));
        return Result.ok(managerService.signedUrl(id, ttl));
    }

    @RequirePermission(permissions = InfraKeys.PERMISSION_FILE_QUERY)
    @GetMapping("/{id}/preview")
    public void preview(@PathVariable long id, HttpServletResponse response) throws IOException {
        FileManagerService.DownloadPayload payload = managerService.preview(id);
        response.setContentType(payload.contentType());
        response.setHeader("Content-Security-Policy", PreviewGuard.CONTENT_SECURITY_POLICY);
        response.setHeader("X-Content-Type-Options", PreviewGuard.CONTENT_TYPE_OPTIONS);
        response.setHeader("Content-Disposition", "inline; filename*=UTF-8''"
                + managerService.encodeFilename(payload.filename()));
        response.getOutputStream().write(payload.data());
    }

    @RequirePermission(permissions = InfraKeys.PERMISSION_FILE_DELETE)
    @DeleteMapping("/{id}")
    public Result<Void> recycle(@PathVariable long id) {
        managerService.recycle(id);
        return Result.ok();
    }

    @RequirePermission(permissions = InfraKeys.PERMISSION_FILE_DELETE)
    @PostMapping("/{id}/restore")
    public Result<Void> restore(@PathVariable long id) {
        managerService.restore(id);
        return Result.ok();
    }

    @RequirePermission(permissions = InfraKeys.PERMISSION_FILE_DELETE)
    @DeleteMapping("/{id}/purge")
    public Result<Void> purge(@PathVariable long id) {
        managerService.purge(id);
        return Result.ok();
    }

    @RequirePermission(permissions = InfraKeys.PERMISSION_FILE_DELETE)
    @PostMapping("/batch-delete")
    public Result<Map<String, Object>> batchDelete(@RequestBody Map<String, Object> body) {
        Object rawIds = body.get("ids");
        cn.slainc.core.lang.BizAssert.isTrue(rawIds instanceof List<?>, "文件编号列表不能为空");
        List<Long> ids = ((List<?>) rawIds).stream().map(value -> Long.parseLong(String.valueOf(value))).toList();
        boolean purge = Boolean.parseBoolean(String.valueOf(body.getOrDefault("purge", "false")));
        return Result.ok(managerService.batchDelete(ids, purge));
    }

    @RequirePermission(permissions = InfraKeys.PERMISSION_FILE_MANAGE)
    @PostMapping("/{id}/rename")
    public Result<Void> rename(@PathVariable long id, @RequestBody Map<String, Object> body) {
        Object name = body.get("name");
        managerService.rename(id, name == null ? null : String.valueOf(name));
        return Result.ok();
    }

    @RequirePermission(permissions = InfraKeys.PERMISSION_FILE_MANAGE)
    @PostMapping("/{id}/move")
    public Result<Void> move(@PathVariable long id, @RequestBody Map<String, Object> body) {
        managerService.move(id, body.get("folder") == null ? "" : String.valueOf(body.get("folder")));
        return Result.ok();
    }

    @RequirePermission(permissions = InfraKeys.PERMISSION_FILE_UPLOAD)
    @PostMapping("/chunk/init")
    public Result<ChunkUploadService.ChunkInitView> chunkInit(@RequestBody Map<String, Object> body) {
        long totalSize = Long.parseLong(String.valueOf(body.get("totalSize")));
        return Result.ok(chunkService.init(String.valueOf(body.get("filename")), totalSize,
                body.get("contentType") == null ? "application/octet-stream" : String.valueOf(body.get("contentType")),
                body.get("folder") == null ? "" : String.valueOf(body.get("folder"))));
    }

    @RequirePermission(permissions = InfraKeys.PERMISSION_FILE_UPLOAD)
    @PostMapping("/chunk/{uploadId}/part")
    public Result<ChunkUploadService.ChunkProgress> chunkPart(@PathVariable String uploadId,
                                                              @RequestParam("partNumber") int partNumber,
                                                              @RequestParam("file") MultipartFile file)
            throws IOException {
        return Result.ok(chunkService.uploadPart(uploadId, partNumber, file));
    }

    @RequirePermission(permissions = InfraKeys.PERMISSION_FILE_UPLOAD)
    @GetMapping("/chunk/{uploadId}/parts")
    public Result<Map<String, Object>> chunkParts(@PathVariable String uploadId) {
        return Result.ok(chunkService.parts(uploadId));
    }

    @RequirePermission(permissions = InfraKeys.PERMISSION_FILE_UPLOAD)
    @PostMapping("/chunk/{uploadId}/complete")
    public Result<FileRecordStore.FileView> chunkComplete(@PathVariable String uploadId) {
        return Result.ok(chunkService.complete(uploadId));
    }

    @RequirePermission(permissions = InfraKeys.PERMISSION_FILE_UPLOAD)
    @PostMapping("/chunk/{uploadId}/abort")
    public Result<Void> chunkAbort(@PathVariable String uploadId) {
        chunkService.abort(uploadId);
        return Result.ok();
    }
}
