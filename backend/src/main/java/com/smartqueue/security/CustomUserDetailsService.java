package com.smartqueue.security;

import com.smartqueue.model.User;
import com.smartqueue.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        if (!StringUtils.hasText(email)) {
            throw new UsernameNotFoundException("Authentication failed: Email cannot be empty");
        }

        // Defensive: Trim spaces and normalize casing to prevent frustrating ghost bugs
        // caused by mobile auto-capitalization or copy-paste trailing spaces.
        String cleanEmail = email.trim().toLowerCase();

        User user = userRepository.findByEmail(cleanEmail)
                .orElseThrow(() -> {
                    // Use DEBUG level to prevent brute-force dictionary attacks from bloating
                    // production disk logs
                    // while still giving us tracing visibility during development.
                    log.debug("Authentication failed: No user found for normalized email [{}]", cleanEmail);

                    // Security best practice: Never reveal if the email actually exists in the
                    // system
                    return new UsernameNotFoundException("Invalid credentials");
                });

        // Defensive: Default to least-privileged role if database record is somehow
        // malformed
        String roleName = user.getRole() != null ? user.getRole().name() : "USER";

        // Let Spring natively block access if the account flag is flipped off rather
        // than doing it manually
        boolean isActive = Boolean.TRUE.equals(user.getIsActive());

        // Favor the builder over the messy 7-arg constructor for long-term readability
        return org.springframework.security.core.userdetails.User.builder()
                .username(user.getEmail())
                // Ensure we don't pass null passwords into Spring Security which triggers
                // opaque NPEs
                .password(user.getPasswordHash() != null ? user.getPasswordHash() : "")
                .disabled(!isActive)
                .accountExpired(false)
                .accountLocked(false)
                .credentialsExpired(false)
                .authorities(List.of(new SimpleGrantedAuthority("ROLE_" + roleName)))
                .build();
    }
}
