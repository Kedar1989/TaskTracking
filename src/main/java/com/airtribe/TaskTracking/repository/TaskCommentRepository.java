package com.airtribe.TaskTracking.repository;

import com.airtribe.TaskTracking.model.TaskComment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TaskCommentRepository extends JpaRepository<TaskComment, Long> {
    List<TaskComment> findByTask_IdOrderByCreatedAtAsc(Long taskId);
}
