package com.airtribe.TaskTracking.controller;

import com.airtribe.TaskTracking.dto.TaskDtos;
import com.airtribe.TaskTracking.model.TaskStatus;
import com.airtribe.TaskTracking.service.TaskService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/tasks")
@RequiredArgsConstructor
public class TaskController {
    private final TaskService taskService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TaskDtos.TaskResponse create(
            @AuthenticationPrincipal String email,
            @Valid @RequestBody TaskDtos.CreateRequest request) {
        return taskService.create(email, request);
    }

    @GetMapping
    public List<TaskDtos.TaskResponse> list(
            @AuthenticationPrincipal String email,
            @RequestParam(required = false) TaskStatus status,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Long teamId,
            @RequestParam(defaultValue = "false") boolean assignedToMe,
            @RequestParam(defaultValue = "updatedAt") String sort,
            @RequestParam(defaultValue = "desc") String direction) {
        return taskService.list(email, status, search, teamId, assignedToMe, sort, direction);
    }

    @GetMapping("/{taskId}")
    public TaskDtos.TaskResponse get(
            @PathVariable Long taskId,
            @AuthenticationPrincipal String email) {
        return taskService.get(taskId, email);
    }

    @PutMapping("/{taskId}")
    public TaskDtos.TaskResponse update(
            @PathVariable Long taskId,
            @AuthenticationPrincipal String email,
            @Valid @RequestBody TaskDtos.UpdateRequest request) {
        return taskService.update(taskId, email, request);
    }

    @PatchMapping("/{taskId}/status")
    public TaskDtos.TaskResponse updateStatus(
            @PathVariable Long taskId,
            @AuthenticationPrincipal String email,
            @Valid @RequestBody TaskDtos.StatusRequest request) {
        return taskService.updateStatus(taskId, email, request.status());
    }

    @DeleteMapping("/{taskId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long taskId, @AuthenticationPrincipal String email) {
        taskService.delete(taskId, email);
    }
}
