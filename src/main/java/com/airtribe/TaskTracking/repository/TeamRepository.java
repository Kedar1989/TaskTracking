package com.airtribe.TaskTracking.repository;

import com.airtribe.TaskTracking.model.Team;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TeamRepository extends JpaRepository<Team, Long> {
    List<Team> findDistinctByMembers_UserId(Long userId);

    Optional<Team> findByIdAndMembers_UserId(Long id, Long userId);
}
