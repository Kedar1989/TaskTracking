package com.airtribe.TaskTracking.controller;

import com.airtribe.TaskTracking.dto.CollaborationDtos;
import com.airtribe.TaskTracking.service.AttachmentService;
import com.airtribe.TaskTracking.service.CommentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;

@RestController
@RequestMapping("/api/tasks/{taskId}")
@RequiredArgsConstructor
public class CollaborationController {
    private final CommentService commentService;
    private final AttachmentService attachmentService;

    @GetMapping("/comments")
    public List<CollaborationDtos.CommentResponse> listComments(
            @PathVariable Long taskId,
            @AuthenticationPrincipal String email) {
        return commentService.list(taskId, email);
    }

    @PostMapping("/comments")
    public ResponseEntity<CollaborationDtos.CommentResponse> addComment(
            @PathVariable Long taskId,
            @AuthenticationPrincipal String email,
            @Valid @RequestBody CollaborationDtos.CreateCommentRequest request) {
        return ResponseEntity.status(201).body(commentService.add(taskId, email, request));
    }

    @GetMapping("/attachments")
    public List<CollaborationDtos.AttachmentResponse> listAttachments(
            @PathVariable Long taskId,
            @AuthenticationPrincipal String email) {
        return attachmentService.list(taskId, email);
    }

    @PostMapping(value = "/attachments", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<CollaborationDtos.AttachmentResponse> addAttachment(
            @PathVariable Long taskId,
            @AuthenticationPrincipal String email,
            @RequestParam("file") MultipartFile file) {
        return ResponseEntity.status(201).body(attachmentService.add(taskId, email, file));
    }

    @GetMapping("/attachments/{attachmentId}")
    public ResponseEntity<Resource> downloadAttachment(
            @PathVariable Long taskId,
            @PathVariable Long attachmentId,
            @AuthenticationPrincipal String email) {
        AttachmentService.Download download = attachmentService.download(taskId, attachmentId, email);
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(download.attachment().getFileName(), StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(download.attachment().getContentType()))
                .contentLength(download.attachment().getSize())
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .header("X-Content-Type-Options", "nosniff")
                .body(download.resource());
    }
}
