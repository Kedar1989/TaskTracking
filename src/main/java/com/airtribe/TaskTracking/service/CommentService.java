package com.airtribe.TaskTracking.service;

import com.airtribe.TaskTracking.dto.CollaborationDtos;
import com.airtribe.TaskTracking.model.Task;
import com.airtribe.TaskTracking.model.TaskComment;
import com.airtribe.TaskTracking.model.Users;
import com.airtribe.TaskTracking.repository.TaskCommentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CommentService {
    private final TaskCommentRepository commentRepository;
    private final TaskService taskService;

    @Transactional(readOnly = true)
    public List<CollaborationDtos.CommentResponse> list(Long taskId, String email) {
        taskService.requireAccessibleTask(taskId, email);
        return commentRepository.findByTask_IdOrderByCreatedAtAsc(taskId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public CollaborationDtos.CommentResponse add(
            Long taskId, String email, CollaborationDtos.CreateCommentRequest request) {
        Task task = taskService.requireAccessibleTask(taskId, email);
        Users author = taskService.findUser(email);
        TaskComment comment = new TaskComment();
        comment.setTask(task);
        comment.setAuthor(author);
        comment.setContent(request.content().trim());
        return toResponse(commentRepository.save(comment));
    }

    private CollaborationDtos.CommentResponse toResponse(TaskComment comment) {
        return new CollaborationDtos.CommentResponse(
                comment.getId(),
                comment.getContent(),
                comment.getAuthor().getUserId(),
                comment.getAuthor().getName(),
                comment.getCreatedAt());
    }
}
