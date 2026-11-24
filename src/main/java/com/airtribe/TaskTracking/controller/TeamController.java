package com.airtribe.TaskTracking.controller;

import com.airtribe.TaskTracking.dto.TeamDtos;
import com.airtribe.TaskTracking.service.TeamService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/teams")
@RequiredArgsConstructor
public class TeamController {
    private final TeamService teamService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TeamDtos.TeamResponse create(
            @AuthenticationPrincipal String email,
            @Valid @RequestBody TeamDtos.CreateRequest request) {
        return teamService.create(email, request);
    }

    @GetMapping
    public List<TeamDtos.TeamResponse> list(@AuthenticationPrincipal String email) {
        return teamService.list(email);
    }

    @GetMapping("/{teamId}")
    public TeamDtos.TeamResponse get(
            @PathVariable Long teamId,
            @AuthenticationPrincipal String email) {
        return teamService.get(teamId, email);
    }

    @PostMapping("/{teamId}/members")
    public TeamDtos.TeamResponse addMember(
            @PathVariable Long teamId,
            @AuthenticationPrincipal String email,
            @Valid @RequestBody TeamDtos.AddMemberRequest request) {
        return teamService.addMember(teamId, email, request.email());
    }
}
