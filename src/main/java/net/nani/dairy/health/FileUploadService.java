package net.nani.dairy.health;

import net.nani.dairy.health.dto.FileUploadResponse;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
public class FileUploadService {

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("jpg", "jpeg", "png", "webp", "pdf");
    private static final Path UPLOADS_ROOT = Path.of("data", "uploads").toAbsolutePath().normalize();

    public FileUploadResponse savePrescription(MultipartFile file) {
        return save(file, "prescriptions", "RX");
    }

    public FileUploadResponse saveQcLab(MultipartFile file) {
        return save(file, "qc-labs", "QC");
    }

    private FileUploadResponse save(MultipartFile file, String categoryFolder, String idPrefix) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("file is required");
        }

        String originalName = sanitizeOriginalName(file.getOriginalFilename());
        String ext = extractExtension(originalName);
        if (!ALLOWED_EXTENSIONS.contains(ext)) {
            throw new IllegalArgumentException("unsupported file type");
        }

        String safeExt = ext.toLowerCase(Locale.ROOT);
        String generatedName = idPrefix + "_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12) + "." + safeExt;
        String folder = LocalDate.now().toString();

        Path targetDir = UPLOADS_ROOT.resolve(Path.of(categoryFolder, folder)).normalize();
        Path targetFile = targetDir.resolve(generatedName).normalize();

        if (!targetFile.startsWith(UPLOADS_ROOT)) {
            throw new IllegalArgumentException("invalid file path");
        }

        try {
            Files.createDirectories(targetDir);
            try (InputStream input = file.getInputStream()) {
                Files.copy(input, targetFile, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            throw new IllegalStateException("could not save file", e);
        }

        return FileUploadResponse.builder()
                .fileName(generatedName)
                .url("/uploads/" + categoryFolder + "/" + folder + "/" + generatedName)
                .contentType(file.getContentType())
                .sizeBytes(file.getSize())
                .build();
    }

    private String sanitizeOriginalName(String name) {
        String cleaned = name == null ? "" : name.trim();
        if (cleaned.isEmpty()) {
            return "file.bin";
        }
        return cleaned.replace("\\", "/");
    }

    private String extractExtension(String fileName) {
        int dot = fileName.lastIndexOf('.');
        if (dot < 0 || dot == fileName.length() - 1) {
            return "";
        }
        return fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
    }
}
