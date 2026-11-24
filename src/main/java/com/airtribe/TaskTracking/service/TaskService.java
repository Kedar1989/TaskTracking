package com.airtribe.TaskTracking.service;

import com.airtribe.TaskTracking.dto.TaskDtos;
import com.airtribe.TaskTracking.exception.ResourceNotFound;
import com.airtribe.TaskTracking.model.Task;
import com.airtribe.TaskTracking.model.TaskStatus;
import com.airtribe.TaskTracking.model.Team;
import com.airtribe.TaskTracking.model.Users;
import com.airtribe.TaskTracking.repository.TaskRepository;
import com.airtribe.TaskTracking.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class TaskService {
    private final TaskRepository taskRepository;
    private final UserRepository userRepository;
    private final TeamService teamService;
    private final AttachmentStorageService attachmentStorageService;

    @Transactional
    public TaskDtos.TaskResponse create(String email, TaskDtos.CreateRequest request) {
        Users creator = findUser(email);
        Task task = new Task();
        task.setTitle(request.title().trim());
        task.setDescription(request.description());
        task.setDueDate(request.dueDate());
        task.setCreatedBy(creator);
        if (request.teamId() != null) {
            task.setTeam(teamService.findMemberTeam(request.teamId(), email));
        }
        task.setAssignee(resolveAssignee(request.assigneeEmail(), task.getTeam()));
        return toResponse(taskRepository.save(task));
    }

    @Transactional(readOnly = true)
    public List<TaskDtos.TaskResponse> list(
            String email,
            TaskStatus status,
            String search,
            Long teamId,
            boolean assignedToMe,
            String sort,
            String direction) {
        Users user = findUser(email);
        String normalizedSearch = search == null ? "" : search.trim().toLowerCase(Locale.ROOT);
        Comparator<Task> comparator = comparatorFor(sort, direction);
        return taskRepository.findAll().stream()
                .filter(task -> canAccess(task, email))
                .filter(task -> status == null || task.getStatus() == status)
                .filter(task -> teamId == null || (task.getTeam() != null && teamId.equals(task.getTeam().getId())))
                .filter(task -> !assignedToMe || (task.getAssignee() != null
                        && task.getAssignee().getUserId().equals(user.getUserId())))
                .filter(task -> normalizedSearch.isEmpty()
                        || contains(task.getTitle(), normalizedSearch)
                        || contains(task.getDescription(), normalizedSearch))
                .sorted(comparator)
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public TaskDtos.TaskResponse get(Long id, String email) {
        return toResponse(requireAccessibleTask(id, email));
    }

    @Transactional
    public TaskDtos.TaskResponse update(Long id, String email, TaskDtos.UpdateRequest request) {
        Task task = requireAccessibleTask(id, email);
        task.setTitle(request.title().trim());
        task.setDescription(request.description());
        task.setDueDate(request.dueDate());
        if (request.status() != null) {
            task.setStatus(request.status());
        }
        task.setAssignee(resolveAssignee(request.assigneeEmail(), task.getTeam()));
        return toResponse(taskRepository.save(task));
    }

    @Transactional
    public TaskDtos.TaskResponse updateStatus(Long id, String email, TaskStatus status) {
        if (status == null) {
            throw new IllegalArgumentException("Status is required");
        }
        Task task = requireAccessibleTask(id, email);
        task.setStatus(status);
        return toResponse(taskRepository.save(task));
    }

    @Transactional
    public void delete(Long id, String email) {
        Task task = requireAccessibleTask(id, email);
        if (!task.getCreatedBy().getEmail().equalsIgnoreCase(email)) {
            throw new AccessDeniedException("Only the task creator can delete it");
        }
        task.getAttachments().forEach(attachment -> attachmentStorageService.delete(attachment.getStorageKey()));
        taskRepository.delete(task);
    }

    @Transactional(readOnly = true)
    public Task requireAccessibleTask(Long id, String email) {
        Task task = taskRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFound("Task not found"));
        if (!canAccess(task, email)) {
            throw new ResourceNotFound("Task not found");
        }
        return task;
    }

    @Transactional(readOnly = true)
    public boolean canAccess(Task task, String email) {
        if (task.getCreatedBy().getEmail().equalsIgnoreCase(email)
                || (task.getAssignee() != null && task.getAssignee().getEmail().equalsIgnoreCase(email))) {
            return true;
        }
        return task.getTeam() != null && task.getTeam().getMembers().stream()
                .anyMatch(member -> member.getEmail().equalsIgnoreCase(email));
    }

    @Transactional(readOnly = true)
    public Users findUser(String email) {
        return userRepository.findByEmailIgnoreCase(email.trim().toLowerCase(Locale.ROOT))
                .orElseThrow(() -> new ResourceNotFound("User not found"));
    }

    private Users resolveAssignee(String assigneeEmail, Team team) {
        if (assigneeEmail == null || assigneeEmail.isBlank()) {
            return null;
        }
        Users assignee = findUser(assigneeEmail);
        if (team == null || team.getMembers().stream()
                .noneMatch(member -> member.getUserId().equals(assignee.getUserId()))) {
            throw new IllegalArgumentException("Assignee must be a member of the task team");
        }
        return assignee;
    }

    private Comparator<Task> comparatorFor(String sort, String direction) {
        String selected = sort == null ? "updatedAt" : sort;
        String order = direction == null ? "desc" : direction.toLowerCase(Locale.ROOT);
        if (!order.equals("asc") && !order.equals("desc")) {
            throw new IllegalArgumentException("Sort direction must be 'asc' or 'desc'");
        }
        Comparator<LocalDate> dueDateOrder = order.equals("desc")
                ? Comparator.reverseOrder() : Comparator.naturalOrder();
        Comparator<Task> comparator = switch (selected) {
            case "title" -> Comparator.comparing(Task::getTitle, String.CASE_INSENSITIVE_ORDER);
            case "dueDate" -> Comparator.comparing(
                    Task::getDueDate,
                    Comparator.nullsLast(dueDateOrder));
            case "status" -> Comparator.comparing(Task::getStatus);
            case "createdAt" -> Comparator.comparing(Task::getCreatedAt);
            case "updatedAt" -> Comparator.comparing(Task::getUpdatedAt);
            default -> throw new IllegalArgumentException("Unsupported sort field");
        };
        return order.equals("desc") && !selected.equals("dueDate") ? comparator.reversed() : comparator;
    }

    private boolean contains(String value, String query) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(query);
    }

    private TaskDtos.TaskResponse toResponse(Task task) {
        Team team = task.getTeam();
        Users assignee = task.getAssignee();
        return new TaskDtos.TaskResponse(
                task.getId(),
                task.getTitle(),
                task.getDescription(),
                task.getDueDate(),
                task.getStatus(),
                team == null ? null : team.getId(),
                team == null ? null : team.getName(),
                assignee == null ? null : assignee.getUserId(),
                assignee == null ? null : assignee.getName(),
                assignee == null ? null : assignee.getEmail(),
                task.getCreatedBy().getUserId(),
                task.getCreatedAt(),
                task.getUpdatedAt());
    }
}
