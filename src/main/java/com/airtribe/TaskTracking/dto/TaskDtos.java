package com.airtribe.TaskTracking.dto;

import com.airtribe.TaskTracking.model.TaskStatus;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.time.LocalDate;

public final class TaskDtos {
    private TaskDtos() {
    }

    public record CreateRequest(
            @NotBlank @Size(max = 200) String title,
            @Size(max = 10000) String description,
            LocalDate dueDate,
            Long teamId,
            @Email @Size(max = 254) String assigneeEmail) {
    }

    public record UpdateRequest(
            @NotBlank @Size(max = 200) String title,
            @Size(max = 10000) String description,
            LocalDate dueDate,
            TaskStatus status,
            @Email @Size(max = 254) String assigneeEmail) {
    }

    public record StatusRequest(@NotNull TaskStatus status) {
    }

    public record TaskResponse(
            Long id,
            String title,
            String description,
            LocalDate dueDate,
            TaskStatus status,
            Long teamId,
            String teamName,
            Long assigneeId,
            String assigneeName,
            String assigneeEmail,
            Long createdById,
            Instant createdAt,
            Instant updatedAt) {
    }
}
