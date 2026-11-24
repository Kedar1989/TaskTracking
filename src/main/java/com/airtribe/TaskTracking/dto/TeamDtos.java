package com.airtribe.TaskTracking.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

public final class TeamDtos {
    private TeamDtos() {
    }

    public record CreateRequest(
            @NotBlank @Size(max = 120) String name,
            @Size(max = 2000) String description,
            List<@NotBlank @Email @Size(max = 254) String> memberEmails) {
    }

    public record AddMemberRequest(@NotBlank @Email String email) {
    }

    public record MemberResponse(Long id, String name, String email) {
    }

    public record TeamResponse(
            Long id,
            String name,
            String description,
            MemberResponse owner,
            List<MemberResponse> members,
            Instant createdAt) {
    }
}
