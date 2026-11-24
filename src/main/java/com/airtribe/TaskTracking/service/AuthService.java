package com.airtribe.TaskTracking.service;

import com.airtribe.TaskTracking.dto.AuthDtos;
import com.airtribe.TaskTracking.exception.ConflictException;
import com.airtribe.TaskTracking.exception.ResourceNotFound;
import com.airtribe.TaskTracking.model.Users;
import com.airtribe.TaskTracking.repository.UserRepository;
import com.airtribe.TaskTracking.security.JwtTokenService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.nio.charset.StandardCharsets;

@Service
@RequiredArgsConstructor
public class AuthService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtTokenService tokenService;

    @Transactional
    public AuthDtos.AuthResponse register(AuthDtos.RegisterRequest request) {
        String email = normalizeEmail(request.email());
        if (request.password().getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new IllegalArgumentException("Password must not exceed 72 UTF-8 bytes");
        }
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new ConflictException("An account with this email already exists");
        }
        Users user = new Users();
        user.setName(request.name().trim());
        user.setEmail(email);
        user.setContactNumber(request.contactNumber());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        return response(userRepository.save(user));
    }

    @Transactional(readOnly = true)
    public AuthDtos.AuthResponse login(AuthDtos.LoginRequest request) {
        String email = normalizeEmail(request.email());
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(email, request.password()));
        return response(findByEmail(email));
    }

    @Transactional(readOnly = true)
    public AuthDtos.UserResponse getProfile(String email) {
        return toUserResponse(findByEmail(email));
    }

    @Transactional
    public AuthDtos.UserResponse updateProfile(String email, AuthDtos.UpdateProfileRequest request) {
        Users user = findByEmail(email);
        user.setName(request.name().trim());
        user.setContactNumber(request.contactNumber());
        return toUserResponse(userRepository.save(user));
    }

    public void logout(String bearerToken) {
        tokenService.revoke(bearerToken);
    }

    private AuthDtos.AuthResponse response(Users user) {
        return new AuthDtos.AuthResponse(
                tokenService.issue(user.getEmail()),
                "Bearer",
                tokenService.getExpirationSeconds(),
                toUserResponse(user));
    }

    private AuthDtos.UserResponse toUserResponse(Users user) {
        return new AuthDtos.UserResponse(user.getUserId(), user.getName(), user.getEmail(), user.getContactNumber());
    }

    private Users findByEmail(String email) {
        return userRepository.findByEmailIgnoreCase(normalizeEmail(email))
                .orElseThrow(() -> new ResourceNotFound("User not found"));
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
