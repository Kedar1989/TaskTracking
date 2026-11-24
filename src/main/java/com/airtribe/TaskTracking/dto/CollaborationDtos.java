package com.airtribe.TaskTracking.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public final class CollaborationDtos {
    private CollaborationDtos() {
    }

    public record CreateCommentRequest(@NotBlank @Size(max = 5000) String content) {
    }

    public record CommentResponse(
            Long id,
            String content,
            Long authorId,
            String authorName,
            Instant createdAt) {
    }

    public record AttachmentResponse(
            Long id,
            String fileName,
            String contentType,
            long size,
            Long uploadedById,
            String uploadedByName,
            Instant uploadedAt) {
    }
}
