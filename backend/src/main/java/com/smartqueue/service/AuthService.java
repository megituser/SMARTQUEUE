package com.smartqueue.service;

import com.smartqueue.dto.request.LoginRequest;
import com.smartqueue.dto.request.RegisterRequest;
import com.smartqueue.dto.response.AuthResponse;
import com.smartqueue.exception.ResourceNotFoundException;
import com.smartqueue.model.Branch;
import com.smartqueue.model.RefreshToken;
import com.smartqueue.model.User;
import com.smartqueue.repository.BranchRepository;
import com.smartqueue.repository.RefreshTokenRepository;
import com.smartqueue.repository.UserRepository;
import com.smartqueue.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private final UserRepository userRepository;
    private final BranchRepository branchRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider jwtTokenProvider;

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String cleanEmail = normalizeEmail(request.getEmail());

        if (userRepository.existsByEmail(cleanEmail)) {
            log.debug("Registration blocked: Email {} is already in use by another account", cleanEmail);
            throw new IllegalStateException("Email is already registered");
        }

        Branch branch = null;
        if (request.getBranchId() != null) {
            branch = branchRepository.findById(request.getBranchId())
                    .orElseThrow(() -> new ResourceNotFoundException("Branch not found", request.getBranchId()));
        }

        User user = User.builder()
                .email(cleanEmail)
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .phone(request.getPhone())
                .role(request.getRole())
                .branch(branch)
                .isActive(true) // Defensive: Explicitly ensure they can login immediately
                .build();

        try {
            user = userRepository.save(user);
        } catch (DataIntegrityViolationException e) {
            // Solves the Time-Of-Check to Time-Of-Use race condition if two users
            // register the same email at the exact same millisecond.
            log.warn("Database unique constraint caught a concurrent duplicate registration for {}", cleanEmail);
            throw new IllegalStateException("Email is already registered");
        }

        log.info("Successfully registered new user: {} (Role: {})", user.getEmail(), user.getRole());

        // Automatically authenticate them to skip forcing a separate login round-trip
        Authentication auth = authenticateCredentials(cleanEmail, request.getPassword());
        return generateAuthPayload(auth, user);
    }

    @Transactional
    public AuthResponse login(LoginRequest request) {
        String cleanEmail = normalizeEmail(request.getEmail());

        // This implicitly executes CustomUserDetailsService, checking password and
        // basic states natively
        Authentication auth = authenticateCredentials(cleanEmail, request.getPassword());

        User user = userRepository.findByEmail(cleanEmail)
                .orElseThrow(
                        () -> new IllegalStateException("Authentication succeeded but internal user record vanished"));

        if (!Boolean.TRUE.equals(user.getIsActive())) {
            log.warn("Login rejected: Account {} is actively disabled", cleanEmail);
            throw new DisabledException("Account has been deactivated");
        }

        log.info("User logged in successfully: {}", cleanEmail);
        return generateAuthPayload(auth, user);
    }

    @Transactional
    public AuthResponse refreshToken(String tokenValue) {
        if (!StringUtils.hasText(tokenValue)) {
            throw new IllegalArgumentException("Refresh token is required");
        }

        // Lock row to prevent token rotation race conditions (e.g., from
        // double-clicking
        // a button or rapid network retries throwing "token revoked" erroneously).
        RefreshToken currentToken = refreshTokenRepository.findWithLockByToken(tokenValue)
                .orElseThrow(() -> {
                    log.debug("Token rotation failed: Refresh token not found in database");
                    return new IllegalStateException("Invalid refresh session");
                });

        if (Boolean.TRUE.equals(currentToken.getRevoked())
                || currentToken.getExpiresAt().isBefore(LocalDateTime.now())) {
            log.warn("Security Event: Attempt to use an expired or revoked refresh token for user {}",
                    currentToken.getUser().getEmail());
            // In a strict security setup, detecting a revoked token reuse assumes token
            // theft and you'd strip ALL active tokens.
            // Securely failing soft for now.
            throw new IllegalStateException("Session expired. Please sign in again.");
        }

        // Rotate out the old token securely
        currentToken.setRevoked(true);
        refreshTokenRepository.save(currentToken);

        User user = currentToken.getUser();
        String newAccessToken = jwtTokenProvider.generateAccessToken(user.getEmail());
        RefreshToken nextToken = createRefreshTokenRecord(user);

        log.debug("Rotated refresh token for user {}", user.getEmail());

        return AuthResponse.builder()
                .accessToken(newAccessToken)
                .refreshToken(nextToken.getToken())
                .tokenType("Bearer")
                .expiresIn(jwtTokenProvider.getAccessTokenExpiration() / 1000)
                .user(buildUserContext(user))
                .build();
    }

    @Transactional
    public void logout(String tokenValue) {
        if (!StringUtils.hasText(tokenValue))
            return;

        // Lock to ensure atomicity on the logout transition
        refreshTokenRepository.findWithLockByToken(tokenValue)
                .ifPresent(token -> {
                    token.setRevoked(true);
                    refreshTokenRepository.save(token);
                    log.debug("Revoked refresh token during explicit logout for user {}", token.getUser().getEmail());
                });
    }

    // =================================================================================================
    // Internal Utilities
    // =================================================================================================

    private String normalizeEmail(String email) {
        if (!StringUtils.hasText(email)) {
            throw new IllegalArgumentException("Email cannot be empty");
        }
        // Drastically reduces authentication headaches caused by trailing spaces or
        // auto-capitalization on mobile
        return email.trim().toLowerCase();
    }

    private Authentication authenticateCredentials(String email, String rawPassword) {
        try {
            return authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(email, rawPassword));
        } catch (BadCredentialsException e) {
            log.debug("Failed login attempt for email: {}", email);
            // Replace ugly native stack traces with a clean unified message string for the
            // frontend
            throw new IllegalStateException("Invalid email or password");
        }
    }

    private AuthResponse generateAuthPayload(Authentication authentication, User user) {
        String accessToken = jwtTokenProvider.generateAccessToken(authentication);
        RefreshToken refreshToken = createRefreshTokenRecord(user);

        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken.getToken())
                .tokenType("Bearer")
                .expiresIn(jwtTokenProvider.getAccessTokenExpiration() / 1000)
                .user(buildUserContext(user))
                .build();
    }

    private RefreshToken createRefreshTokenRecord(User user) {
        RefreshToken token = RefreshToken.builder()
                .user(user)
                .token(UUID.randomUUID().toString())
                // Fixed 7-day expiration.
                .expiresAt(LocalDateTime.now().plusDays(7))
                .revoked(false)
                .build();

        return refreshTokenRepository.save(token);
    }

    private AuthResponse.UserResponse buildUserContext(User user) {
        var builder = AuthResponse.UserResponse.builder()
                .id(user.getId())
                .email(user.getEmail())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .role(user.getRole() != null ? user.getRole().name() : "USER");

        if (user.getBranch() != null) {
            builder.branchId(user.getBranch().getId())
                    .branchName(user.getBranch().getName());
        }

        return builder.build();
    }
}
