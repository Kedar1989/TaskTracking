package com.airtribe.TaskTracking.service;

import com.airtribe.TaskTracking.dto.CollaborationDtos;
import com.airtribe.TaskTracking.exception.ResourceNotFound;
import com.airtribe.TaskTracking.model.Task;
import com.airtribe.TaskTracking.model.TaskAttachment;
import com.airtribe.TaskTracking.model.Users;
import com.airtribe.TaskTracking.repository.TaskAttachmentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AttachmentService {
    private static final long MAX_FILE_SIZE = 10L * 1024 * 1024;
    private final TaskAttachmentRepository attachmentRepository;
    private final TaskService taskService;
    private final AttachmentStorageService storageService;

    @Transactional(readOnly = true)
    public List<CollaborationDtos.AttachmentResponse> list(Long taskId, String email) {
        taskService.requireAccessibleTask(taskId, email);
        return attachmentRepository.findByTask_IdOrderByUploadedAtAsc(taskId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public CollaborationDtos.AttachmentResponse add(Long taskId, String email, MultipartFile file) {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("Attachment must not be empty");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new IllegalArgumentException("Attachment must be no larger than 10 MB");
        }
        Task task = taskService.requireAccessibleTask(taskId, email);
        Users uploader = taskService.findUser(email);
        String displayName = safeFileName(file.getOriginalFilename());
        String contentType = file.getContentType() == null
                ? "application/octet-stream" : file.getContentType();
        org.springframework.http.MediaType.parseMediaType(contentType);
        String storageKey = storageService.store(file);

        TaskAttachment attachment = new TaskAttachment();
        attachment.setTask(task);
        attachment.setUploadedBy(uploader);
        attachment.setFileName(displayName);
        attachment.setContentType(contentType);
        attachment.setSize(file.getSize());
        attachment.setStorageKey(storageKey);
        try {
            return toResponse(attachmentRepository.saveAndFlush(attachment));
        } catch (RuntimeException e) {
            try {
                storageService.delete(storageKey);
            } catch (RuntimeException cleanupFailure) {
                e.addSuppressed(cleanupFailure);
            }
            throw e;
        }
    }

    @Transactional(readOnly = true)
    public Download download(Long taskId, Long attachmentId, String email) {
        taskService.requireAccessibleTask(taskId, email);
        TaskAttachment attachment = attachmentRepository.findById(attachmentId)
                .filter(item -> item.getTask().getId().equals(taskId))
                .orElseThrow(() -> new ResourceNotFound("Attachment not found"));
        return new Download(attachment, storageService.load(attachment.getStorageKey()));
    }

    private String safeFileName(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return "attachment";
        }
        String sanitized = fileName.replace('\\', '/');
        sanitized = sanitized.substring(sanitized.lastIndexOf('/') + 1)
                .replaceAll("[\\r\\n\\p{Cntrl}]", "_");
        if (sanitized.isBlank()) {
            return "attachment";
        }
        return sanitized.length() > 255 ? sanitized.substring(sanitized.length() - 255) : sanitized;
    }

    private CollaborationDtos.AttachmentResponse toResponse(TaskAttachment attachment) {
        return new CollaborationDtos.AttachmentResponse(
                attachment.getId(),
                attachment.getFileName(),
                attachment.getContentType(),
                attachment.getSize(),
                attachment.getUploadedBy().getUserId(),
                attachment.getUploadedBy().getName(),
                attachment.getUploadedAt());
    }

    public record Download(TaskAttachment attachment, Resource resource) {
    }
}
