package com.airtribe.TaskTracking.controller;

import com.airtribe.TaskTracking.dto.AuthDtos;
import com.airtribe.TaskTracking.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {
    private final AuthService authService;

    @PostMapping("/register")
    public ResponseEntity<AuthDtos.AuthResponse> register(@Valid @RequestBody AuthDtos.RegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.register(request));
    }

    @PostMapping("/login")
    public AuthDtos.AuthResponse login(@Valid @RequestBody AuthDtos.LoginRequest request) {
        return authService.login(request);
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization) {
        authService.logout(authorization.substring("Bearer ".length()));
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    public AuthDtos.UserResponse getProfile(@AuthenticationPrincipal String email) {
        return authService.getProfile(email);
    }

    @PutMapping("/me")
    public AuthDtos.UserResponse updateProfile(
            @AuthenticationPrincipal String email,
            @Valid @RequestBody AuthDtos.UpdateProfileRequest request) {
        return authService.updateProfile(email, request);
    }
}
