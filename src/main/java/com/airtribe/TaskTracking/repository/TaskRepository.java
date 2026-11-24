package com.airtribe.TaskTracking.repository;

import com.airtribe.TaskTracking.model.Task;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TaskRepository extends JpaRepository<Task, Long> {
}
