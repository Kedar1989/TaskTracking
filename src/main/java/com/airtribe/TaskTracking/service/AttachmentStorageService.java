package com.airtribe.TaskTracking.service;

import com.airtribe.TaskTracking.exception.ResourceNotFound;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

@Service
public class AttachmentStorageService {
    @Value("${app.upload-dir}")
    private String uploadDirectory;

    public String store(MultipartFile file) {
        String storageKey = UUID.randomUUID().toString();
        Path target = storageRoot().resolve(storageKey);
        try {
            Files.createDirectories(storageRoot());
            try (InputStream input = file.getInputStream()) {
                Files.copy(input, target);
            }
            return storageKey;
        } catch (IOException e) {
            try {
                Files.deleteIfExists(target);
            } catch (IOException cleanupFailure) {
                e.addSuppressed(cleanupFailure);
            }
            throw new IllegalStateException("Could not store attachment", e);
        }
    }

    public Resource load(String storageKey) {
        try {
            Path root = storageRoot();
            Path file = root.resolve(storageKey).normalize();
            if (!file.startsWith(root) || !Files.isRegularFile(file)) {
                throw new ResourceNotFound("Attachment file not found");
            }
            return new UrlResource(file.toUri());
        } catch (IOException e) {
            throw new IllegalStateException("Could not read attachment", e);
        }
    }

    public void delete(String storageKey) {
        try {
            Path root = storageRoot();
            Path file = root.resolve(storageKey).normalize();
            if (!file.startsWith(root)) {
                throw new IllegalArgumentException("Invalid attachment storage key");
            }
            Files.deleteIfExists(file);
        } catch (IOException e) {
            throw new IllegalStateException("Could not delete attachment file", e);
        }
    }

    private Path storageRoot() {
        return Path.of(uploadDirectory).toAbsolutePath().normalize();
    }
}
