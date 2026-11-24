package com.airtribe.TaskTracking.repository;

import com.airtribe.TaskTracking.model.TaskAttachment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TaskAttachmentRepository extends JpaRepository<TaskAttachment, Long> {
    List<TaskAttachment> findByTask_IdOrderByUploadedAtAsc(Long taskId);
}
