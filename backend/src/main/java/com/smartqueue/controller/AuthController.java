package com.smartqueue.controller;

import com.smartqueue.dto.request.LoginRequest;
import com.smartqueue.dto.request.RefreshTokenRequest;
import com.smartqueue.dto.request.RegisterRequest;
import com.smartqueue.dto.response.ApiResponse;
import com.smartqueue.dto.response.AuthResponse;
import com.smartqueue.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<AuthResponse>> register(@Valid @RequestBody RegisterRequest request) {
        log.info("Registration attempt: email={}", request.getEmail());

        AuthResponse auth = authService.register(request);

        log.info("Registration successful: userId={}", auth.getUser().getId());

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success("Registration successful", auth));
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthResponse>> login(@Valid @RequestBody LoginRequest request) {
        log.info("Login attempt: email={}", request.getEmail());

        AuthResponse auth = authService.login(request);

        log.info("Login successful: userId={}", auth.getUser().getId());

        return ResponseEntity.ok(ApiResponse.success("Login successful", auth));
    }

    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<AuthResponse>> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        AuthResponse auth = authService.refreshToken(request.getRefreshToken());

        return ResponseEntity.ok(ApiResponse.success("Token refreshed", auth));
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(@Valid @RequestBody RefreshTokenRequest request) {
        authService.logout(request.getRefreshToken());

        return ResponseEntity.ok(ApiResponse.success("Logged out successfully", null));
    }
}