package com.example.demo.service;

import com.example.demo.common.BusinessException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
public class FileService {

    private static final Set<String> DOC_EXT = Set.of(
            "doc", "docx", "pdf", "zip", "rar", "7z", "txt", "png", "jpg", "jpeg",
            "xls", "xlsx", "ppt", "pptx", "csv", "md");
    private static final Set<String> IMG_EXT = Set.of("png", "jpg", "jpeg", "webp", "gif");

    @Value("${app.upload-dir:./uploads}")
    private String uploadDir;

    public Path getRoot() {
        return Paths.get(uploadDir).toAbsolutePath().normalize();
    }

    public String storeDocument(MultipartFile file, String subdir) {
        return store(file, subdir, DOC_EXT, 50L * 1024 * 1024);
    }

    public String storeAvatar(MultipartFile file) {
        return store(file, "avatars", IMG_EXT, 2L * 1024 * 1024);
    }

    private String store(MultipartFile file, String subdir, Set<String> allowed, long maxBytes) {
        if (file == null || file.isEmpty()) return null;
        if (file.getSize() > maxBytes) {
            throw new BusinessException("文件过大，最大允许 " + (maxBytes / 1024 / 1024) + "MB");
        }
        String original = file.getOriginalFilename() == null ? "file" : file.getOriginalFilename();
        String ext = original.contains(".") ? original.substring(original.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT) : "";
        if (!allowed.contains(ext)) {
            throw new BusinessException("不支持的文件类型：" + (ext.isEmpty() ? original : ext));
        }
        try {
            Path dir = getRoot().resolve(subdir).normalize();
            Files.createDirectories(dir);
            String name = UUID.randomUUID().toString().replace("-", "") + "." + ext;
            Path target = dir.resolve(name);
            try (InputStream in = file.getInputStream()) {
                Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
            }
            return subdir + "/" + name;
        } catch (IOException e) {
            throw new BusinessException("文件保存失败，请稍后重试");
        }
    }

    public Path resolve(String relative) {
        if (relative == null || relative.isBlank()) return null;
        Path path = getRoot().resolve(relative).normalize();
        if (!path.startsWith(getRoot())) return null;
        return path;
    }

    public void delete(String relative) {
        Path path = resolve(relative);
        if (path == null) return;
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
        }
    }

    public String sizeText(Long size) {
        if (size == null) return "";
        if (size < 1024) return size + " B";
        if (size < 1024 * 1024) return String.format("%.1f KB", size / 1024.0);
        return String.format("%.1f MB", size / 1024.0 / 1024.0);
    }
}
