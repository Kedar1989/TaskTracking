package com.airtribe.TaskTracking.service;

import com.airtribe.TaskTracking.dto.TeamDtos;
import com.airtribe.TaskTracking.exception.ResourceNotFound;
import com.airtribe.TaskTracking.model.Team;
import com.airtribe.TaskTracking.model.Users;
import com.airtribe.TaskTracking.repository.TeamRepository;
import com.airtribe.TaskTracking.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class TeamService {
    private final TeamRepository teamRepository;
    private final UserRepository userRepository;

    @Transactional
    public TeamDtos.TeamResponse create(String email, TeamDtos.CreateRequest request) {
        Users owner = findUser(email);
        Team team = new Team();
        team.setName(request.name().trim());
        team.setDescription(request.description());
        team.setOwner(owner);
        team.getMembers().add(owner);
        if (request.memberEmails() != null) {
            for (String memberEmail : request.memberEmails()) {
                team.getMembers().add(findUser(memberEmail));
            }
        }
        return toResponse(teamRepository.save(team));
    }

    @Transactional
    public TeamDtos.TeamResponse addMember(Long teamId, String requesterEmail, String memberEmail) {
        Team team = findTeam(teamId);
        if (!team.getOwner().getEmail().equalsIgnoreCase(requesterEmail)) {
            throw new AccessDeniedException("Only the team owner can add members");
        }
        team.getMembers().add(findUser(memberEmail));
        return toResponse(teamRepository.save(team));
    }

    @Transactional(readOnly = true)
    public List<TeamDtos.TeamResponse> list(String email) {
        Users user = findUser(email);
        return teamRepository.findDistinctByMembers_UserId(user.getUserId()).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public TeamDtos.TeamResponse get(Long teamId, String email) {
        Users user = findUser(email);
        Team team = teamRepository.findByIdAndMembers_UserId(teamId, user.getUserId())
                .orElseThrow(() -> new ResourceNotFound("Team not found"));
        return toResponse(team);
    }

    @Transactional(readOnly = true)
    public Team findMemberTeam(Long teamId, String email) {
        Users user = findUser(email);
        return teamRepository.findByIdAndMembers_UserId(teamId, user.getUserId())
                .orElseThrow(() -> new ResourceNotFound("Team not found"));
    }

    @Transactional(readOnly = true)
    public Users findUser(String email) {
        return userRepository.findByEmailIgnoreCase(email.trim().toLowerCase(Locale.ROOT))
                .orElseThrow(() -> new ResourceNotFound("User not found"));
    }

    @Transactional(readOnly = true)
    public Team findTeam(Long teamId) {
        return teamRepository.findById(teamId)
                .orElseThrow(() -> new ResourceNotFound("Team not found"));
    }

    private TeamDtos.TeamResponse toResponse(Team team) {
        return new TeamDtos.TeamResponse(
                team.getId(),
                team.getName(),
                team.getDescription(),
                toMember(team.getOwner()),
                team.getMembers().stream().map(this::toMember).toList(),
                team.getCreatedAt());
    }

    private TeamDtos.MemberResponse toMember(Users user) {
        return new TeamDtos.MemberResponse(user.getUserId(), user.getName(), user.getEmail());
    }
}
